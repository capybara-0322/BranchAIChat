from typing import List, Literal
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.pydantic_v1 import BaseModel, Field, create_model
from langchain_openai import ChatOpenAI
import yaml


class QueryRouter:
    """
    A class that routes each question to one of several possible categories
    based on their descriptions, using an LLM with structured output.
    """

    def __init__(self, config, temperature: float = 0):
        """
        Initialize the QueryRouter with a specific model.

        Args:
            config (dict): Configuration dictionary with model info.
            temperature (float): Sampling temperature.
        """
        self.llm = ChatOpenAI(model=config["router_model"], temperature=temperature)

    def route(
            self,
            questions: List[str],
            categories: List[str],
            category_descriptions: List[str],
    ) -> List[dict]:
        """
        Route a list of questions to the most appropriate category.

        Args:
            questions (List[str]): List of user questions.
            categories (List[str]): List of routeable categories.
            category_descriptions (List[str]): Descriptions of what each category covers.

        Returns:
            List[dict]: Each dict contains {'question': str, 'route_type': str}.
        """
        if len(categories) != len(category_descriptions):
            raise ValueError("categories and category_descriptions must have the same length.")

        # Dynamically create a Pydantic model for structured output
        RouteModel = create_model(
            "RouteQuery",
            datasource=(Literal[tuple(categories)], Field(
                ...,
                description="Choose the most relevant category for the user's question."
            )),
        )

        structured_llm = self.llm.with_structured_output(RouteModel)

        # Build routing instruction
        system_prompt = "You are an expert router that assigns each user question to the most suitable category.\n\n"
        system_prompt += "Available categories:\n"
        for cat, desc in zip(categories, category_descriptions):
            system_prompt += f"- {cat}: {desc}\n"
        system_prompt += "\nReturn only the name of the most relevant category."

        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("human", "{question}")
        ])

        chain = prompt | structured_llm

        results = []
        for q in questions:
            output = chain.invoke({"question": q})
            results.append({
                "question": q,
                "route_type": output.datasource
            })

        return results


# ====================
# Example usage
# ====================

def load_config(path="../config.yaml"):
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


if __name__ == "__main__":
    router = QueryRouter(load_config())

    questions = [
        "What is the english score of Tom?",
        "What are the difficulties of RAG?",
        "Explain how async works in JavaScript.",
        "Where does student Marry live?",
    ]

    categories = ["RAG_knowledge", "student_information", "general_knowledge"]
    descriptions = [
        "The questions related to RAG (retrieval-augmented generation).",
        "Questions about information on students, including name, age, address, class, chinese score, english score, math score, class rank, grade rank.",
        "All other general knowledge questions."
    ]

    routes = router.route(questions, categories, descriptions)

    print("\n=== Routing Results ===")
    for item in routes:
        print(f"❓ Question: {item['question']}")
        print(f"➡ Routed to: {item['route_type']}")
