import yaml
from langchain.chat_models import ChatOpenAI
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate, FewShotChatMessagePromptTemplate


class QueryTranslator:
    def __init__(self, config):
        self.enabled = config.get("enable_translation", False)
        self.llm = ChatOpenAI(model=config["translator_model"]) if self.enabled else None
    def _multi_query(self, query: str, top_k=2):
        """生成多样化的查询 (multi_query)"""
        template = (
            "You are an AI language model assistant. Your task is to generate {top_k} "
            "different versions of the given user question to retrieve relevant documents from a vector "
            "database. By generating multiple perspectives on the user question, your goal is to help "
            "the user overcome some of the limitations of the distance-based similarity search. "
            "Provide these alternative questions separated by newlines, one question per line. Original question: {{question}} "
        ).format(top_k = top_k)
        prompt = ChatPromptTemplate.from_template(template)
        generate = prompt | self.llm | StrOutputParser() | (lambda x: x.split("\n"))
        return generate.invoke({"question": query})

    def _step_back(self, query: str):
        """生成 step-back 问题，即更高层次的抽象问题"""
        examples = [
            {
                "input": "Could the members of The Police perform lawful arrests?",
                "output": "what can the members of The Police do?",
            },
            {
                "input": "Jan Sindel’s was born in what country?",
                "output": "what is Jan Sindel’s personal history?",
            },
            {
                "input": "What is AI",
                "output": "None",
            },
        ]
        example_prompt = ChatPromptTemplate.from_messages(
            [
                ("human", "{input}"),
                ("ai", "{output}"),
            ]
        )
        few_shot_prompt = FewShotChatMessagePromptTemplate(
            example_prompt=example_prompt,
            examples=examples,
        )
        prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    """You are an expert at world knowledge. Your task is to step back and paraphrase a question to a more generic step-back question, which is easier to answer.
                     BUT! If you think this question is not worth to step back and paraphrase, or the question is general enough, output exactly the word 'None'.
                     Here are a few examples:""",
                ),
                # Few shot examples
                few_shot_prompt,
                # New question
                ("user", "{question}"),
            ]
        )
        generate = prompt | self.llm | StrOutputParser()
        result  = generate.invoke({"question": query})
        if result.lower() == "none":
            return []
        else:
            # 分割多行子问题
            return [result]




    def _sub_question(self, query: str):
        """将问题分解为子问题"""
        template = (
            "You are an expert reasoning assistant. "
            "Your task is to decide whether the following question should be decomposed into simpler sub-questions.\n\n"
            "If the question is already simple, self-contained, or cannot reasonably be decomposed, "
            "output exactly the word 'None'.\n\n"
            "Otherwise, decompose the question into one to several sub-questions that, when answered, "
            "would help answer the original question.\n\n"
            "Do not add numbering or extra explanations. "
            "Each sub-question must be independent and not rely on the information of other questions, which indicates that you usually cannot use pronouns(such as he she that this)."
            "List each sub-question on a new line.\n\n"
            "Question: {question}"
        )
        prompt = ChatPromptTemplate.from_template(template)
        generate = prompt | self.llm | StrOutputParser()
        output = generate.invoke({"question": query})

        # 后处理：如果模型输出 None 或 none，不再分行处理
        if output.lower() == "none":
            return []
        else:
            # 分割多行子问题
            return output.split("\n")


    def translate(self, query: str, methods=None, top_k=5) -> list[str]:
        """根据指定方法生成 queries"""
        if not self.enabled:
            return [query]


        results = {"original": [query]}

        if "step_back" in methods:
            results["step_back"] = self._step_back(query)
        if "sub_question" in methods:
            results["sub_question"] = self._sub_question(query)
        if "multi_query" in methods:
            results["multi_query"] = self._multi_query(query, top_k=top_k)

        # 按固定顺序拼接结果
        ordered_queries = []
        for key in ["original", "step_back", "sub_question", "multi_query"]:
            if key in results:
                ordered_queries.extend(results[key])

        return ordered_queries




"""
测试代码
"""
def load_config(path="../config.yaml"):
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)

if __name__ == '__main__':
    translater = QueryTranslator(load_config())
    out = translater.translate("Who has the highest score in mathematics in the class and what is his ranking in the class?", methods=["sub_question"])
    print(out)


