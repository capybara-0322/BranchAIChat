from typing import List, Dict

class PromptConstructor:
    def __init__(self, config):
        self.config = config

    def promptconstruct(self, retrieved_data: List[Dict]) -> str:
        """
        输入为Retriever返回的字典数组
        输出为完整的Prompt文本
        """

        if not retrieved_data:
            return "No queries found."

        # 原始问题（第一个）
        main_question = retrieved_data[0].get("question", "Unknown question")
        main_sql = retrieved_data[0].get("sql", "")
        main_route = retrieved_data[0].get("route_type", "")

        # 构造衍生问题段落
        retrieval_blocks = []
        for item in retrieved_data:
            q = item.get("question", "")
            route = item.get("route_type", "")
            sql = item.get("sql", "")

            # 关系型检索结果
            if "relational_retrieve" in item and item["relational_retrieve"]:
                data_str = "\n".join(
                    [f"- {', '.join([f'{k}: {v}' for k, v in row.items()])}"
                     for row in item["relational_retrieve"]]
                )
                block = f"Q: {q}\nType: {route}\nRetrieved Data:\n{data_str}"

            # 向量检索结果
            elif "vector_retrieve" in item and item["vector_retrieve"]:
                docs = "\n".join([f"- \"{doc}\"" for doc in item["vector_retrieve"]])
                block = f"Q: {q}\nType: {route}\nRetrieved Documents:\n{docs}"

            else:
                block = f"Q: {q}\nType: {route}\nRetrieved Data: None"

            retrieval_blocks.append(block)

        retrieval_text = "\n\n".join(retrieval_blocks)

        # 组装最终Prompt
        final_prompt = f"""You are a reasoning assistant. Your goal is to provide a precise and concise answer.

You are given:
1. The original user question.
2. A set of related sub-questions with their retrieved results (from either database or vector search).

Use all the information below to generate the most accurate and direct final answer.
If the retrieved data contains numeric or factual results, rely on them directly.
Do not mention SQL or retrieval sources in your answer.

---

[Original Question]
{main_question}

[Main Query Structure]
Route Type: {main_route}
SQL or Query: {main_sql}

[Related Sub-Questions and Retrieved Results]
{retrieval_text}

---

Now write the final answer in concise natural language:
"""
        return final_prompt


# ========== 测试 ==========
if __name__ == "__main__":
    # 模拟Retriever返回的结果
    test_data = [
        {
            "question": "Which students have math_score > 90?",
            "route_type": "student_information",
            "sql": "SELECT name, math_score FROM students WHERE math_score > 90;",
            "relational_retrieve": [
                {"name": "Alice", "math_score": 95},
                {"name": "Bob", "math_score": 92}
            ]
        },
        {
            "question": "What is RAG in AI?",
            "route_type": "RAG_knowledge",
            "sql": "",
            "vector_retrieve": [
                "RAG combines retrieval and generation to answer questions using external data.",
                "It retrieves documents and uses a language model to synthesize an answer."
            ]
        }
    ]

    prompt_constructor = PromptConstructor(config={})
    final_prompt = prompt_constructor.promptconstruct(test_data)
    print(final_prompt)
