# main.py
import yaml
import os

from sympy.testing.runtests import method

from modules.query_translator_choose import QueryTranslatorChoose
from modules.query_translator import QueryTranslator
from modules.query_router import QueryRouter
from modules.query_constructor import QueryConstructor
from modules.retriever import Retriever
from modules.prompt_construct import PromptConstructor
from modules.generator import Generator

def load_config(path="config.yaml"):
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)

def rag_pipeline(query: str, config):
    print(f"\n[用户输入] {query}")

    # Step 1: 翻译 / 重写查询
    methods = QueryTranslatorChoose(config).choose_translator(query)
    print(f"translate methods: {methods}")
    translator = QueryTranslator(config)
    query_translated = translator.translate(query, methods)

    # Step 2: 路由判断
    router = QueryRouter(config)
    route_type = ["RAG_knowledge", "student_information", "general_knowledge"]
    type_description = ["The questions related to RAG(retrieval-augmented generation).",
                        "The question about information about students, including name, age, address ,class , chinese score, english score, math score, class rank, grade rank",
                        "All other question or query."]
    queries = router.route(query_translated, route_type, type_description)
    print(f"路由类型: {queries}")

    # Step 3: 查询构造
    constructor = QueryConstructor(config)
    expanded_queries = constructor.construct(queries)
    print(f"before retrieve:{expanded_queries}")

    # Step 4: 检索
    retriever = Retriever(config)
    doc_queries = retriever.retrieve(expanded_queries)
    for r in doc_queries:
        print(r)


    # Step 5.1: 组装最终prompt
    promptconstructor = PromptConstructor(config)
    final_prompt = promptconstructor.promptconstruct(doc_queries)
    print(f"final prompt:{final_prompt}")


    # Step 5.2: 生成答案
    generator = Generator(config)
    answer = generator.generate(final_prompt)

    return answer


if __name__ == "__main__":
    config = load_config()
    while True:
        query = input("请输入问题（或输入exit退出）：")
        if query.lower() == "exit":
            break
        print("\n========= RAG 系统响应 =========")
        print(rag_pipeline(query, config))
        print("================================\n")
