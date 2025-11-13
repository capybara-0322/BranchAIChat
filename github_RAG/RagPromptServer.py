from flask import Flask, request, jsonify
import yaml
from modules.query_translator_choose import QueryTranslatorChoose
from modules.query_translator import QueryTranslator
from modules.query_router import QueryRouter
from modules.query_constructor import QueryConstructor
from modules.retriever import Retriever
from modules.prompt_construct import PromptConstructor




def load_config(path="config.yaml"):
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)
app = Flask(__name__)

# 初始化模块
config = load_config("config.yaml")



@app.route("/generate_prompt", methods=["POST"])
def generate_prompt():
    """
    输入: {"question": "学生李明的数学成绩是多少？"}
    输出: {"prompt": "...组装后的prompt字符串..."}
    """
    try:
        data = request.get_json()
        question = data.get("question", "").strip()

        if not question:
            return jsonify({"error": "Missing 'question' field"}), 400

        # ====== RAG 流程 ======
        print(f"\n[用户输入] {question}")

        # Step 1: 翻译 / 重写查询
        methods = QueryTranslatorChoose(config).choose_translator(question)
        print(f"translate methods: {methods}")
        translator = QueryTranslator(config)
        query_translated = translator.translate(question, methods)

        # Step 2: 路由判断
        router = QueryRouter(config)
        route_type = ["RAG_knowledge", "student_information", "general_knowledge"]
        type_description = ["The questions related to RAG(retrieval-augmented generation).",
                            "The question about information about students, including name, age, address ,class , chinese score, english score, math score, class rank, grade rank",
                            "All other question or query."]
        queries = router.route(query_translated, route_type, type_description)

        # Step 3: 查询构造
        constructor = QueryConstructor(config)
        expanded_queries = constructor.construct(queries)

        # Step 4: 检索
        retriever = Retriever(config)
        doc_queries = retriever.retrieve(expanded_queries)

        # Step 5.1: 组装最终prompt
        promptconstructor = PromptConstructor(config)
        final_prompt = promptconstructor.promptconstruct(doc_queries)
        print(f"final prompt")


        return jsonify({
            "question": question,
            "prompt": final_prompt
            # "answer": response  # 若需要可打开
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    # host="0.0.0.0" 可在局域网访问，port 可自定义
    app.run(host="127.0.0.1", port=7000, debug=True)
