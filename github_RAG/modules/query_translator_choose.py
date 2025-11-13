from langchain.chat_models import ChatOpenAI
from langchain.prompts import PromptTemplate
from langchain.schema import StrOutputParser
from langchain.chains import LLMChain
import yaml

# You can change the model to another one, such as "gpt-4o-mini", "gpt-4-turbo", or a local LLM endpoint
MODEL_NAME = "gpt-4o-mini"

class QueryTranslatorChoose:
    """
    This class provides a function to analyze a given query and determine which query rewriting strategies should be applied.
    """
    def __init__(self, config):
        self.enabled = config.get("enable_translation", False)
        self.llm = ChatOpenAI(model=config["translator_choose_model"]) if self.enabled else None


    def choose_translator(self, question: str) -> list:
        """
        Analyze whether a given query should use multi-query expansion,
        step-back reasoning, or sub-question decomposition in a RAG pipeline.

        Returns a Python list of applicable strategies, e.g.:
            ["multi_query", "step_back"]
        or [] if none are needed.
        """

        prompt = PromptTemplate.from_template("""
        You are an intelligent RAG (Retrieval-Augmented Generation) query analyzer.
        Your task is to determine which query rewriting strategies should be applied.
        Only call the strategies when they are truly necessary. Otherwise, you can choose not to use any strategy at all.
    
        Query:
        {question}
    
        Strategy definitions:
        - multi_query: The question could have multiple paraphrases or synonyms, so multiple queries might help improve recall.
        - step_back: The question is too narrow or specific; stepping back to a broader context could improve understanding.
        - sub_question: The question is complex and should be decomposed into smaller, simpler questions.
    
        Please decide which strategies apply to this query.
        Output a Python list only, e.g.:
        ["multi_query", "step_back"]
        or []
        """)

        llm = ChatOpenAI(model=MODEL_NAME, temperature=0)

        chain = LLMChain(
            llm=llm,
            prompt=prompt,
            output_parser=StrOutputParser()
        )

        result = chain.run({"question": question}).strip()

        # Safe parsing
        try:
            strategies = eval(result)
            if isinstance(strategies, list):
                return strategies
            else:
                return []
        except Exception:
            print("Could not parse model output. Raw output:", result)
            return []



"""
测试代码
"""
def load_config(path="../config.yaml"):
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


# ====================
# Example usage
# ====================
if __name__ == "__main__":

    config = load_config()
    test_questions = [
        "Explain how the Transformer model works.",
        "What are some popular museums in Paris?",
        "How can RAG be used for medical literature question answering?",
        "Describe the experimental process and significance of quantum entanglement."
    ]
    for q in test_questions:
        analyze_query_strategy = QueryTranslatorChoose(config).choose_translator(q)
        print(f"\n❓ Question: {q}")
        print("➡ Recommended strategies:", analyze_query_strategy)
