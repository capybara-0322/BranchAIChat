from typing import List, Dict
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate


class QueryConstructor:
    def __init__(self, config):
        self.config = config
        self.llm = ChatOpenAI(
            model=config.get("query_construct_model", "gpt-4o-mini"),
            temperature=config.get("temperature", 0),
        )

        # SQL 生成提示词模板
        self.sql_prompt = ChatPromptTemplate.from_template(
            """You are an expert SQL query generator.
Given a natural language question about students, 
write a SQL SELECT query on the table `students` with columns:
(name, age, class_name, address, math_score, chinese_score, english_score, class_rank, grade_rank).

The SQL should only include necessary columns and conditions.
Use '=' or range conditions like '>' or '<=' when appropriate.
Do NOT include explanation, only output SQL.

Example:
Question: Who is the top student in grade_rank?
SQL: SELECT name, age, class_name, address, math_score, chinese_score, english_score, class_rank, grade_rank FROM students ORDER BY grade_rank LIMIT 1;

Task:
Question: {question}
SQL:"""
        )

    def construct(self, query_items: List[Dict[str, str]]) -> List[Dict[str, str]]:
        """
        输入: [{'question': str, 'route_type': str}, ...]
        输出: [{'question': str, 'route_type': str, 'sql': str}, ...]

        route_type:
        - RAG_knowledge → 空字符串
        - general_knowledge → 空字符串
        - student_information → 调用LLM生成SQL
        """
        results = []
        for item in query_items:
            question = item.get("question", "")
            route_type = item.get("route_type", "")
            sql_result = ""

            if route_type == "student_information":
                try:
                    prompt = self.sql_prompt.format_messages(question=question)
                    sql_response = self.llm.invoke(prompt)
                    sql_result = sql_response.content.strip()
                except Exception as e:
                    print(f"[Error generating SQL for question: {question}] {e}")
                    sql_result = ""

            # 其他类型均返回空SQL
            results.append({
                "question": question,
                "route_type": route_type,
                "sql": sql_result
            })

        return results


"""
===================
测试代码
===================
"""

if __name__ == "__main__":
    config = {
        "query_construct_model": "gpt-4o-mini",
        "temperature": 0
    }

    qc = QueryConstructor(config)

    data = [
        {"question": "Show me all students whose math_score is greater than 90", "route_type": "student_information"},
        {"question": "Who is the top student in grade_rank?", "route_type": "student_information"},
        {"question": "What is the capital of France?", "route_type": "general_knowledge"},
        {"question": "Explain the concept of RAG models", "route_type": "RAG_knowledge"},
        {"question": "List all students and their ages", "route_type": "student_information"},
    ]

    results = qc.construct(data)

    print("\n=== QueryConstructor Test Results ===")
    for item in results:
        print(f"[{item['route_type']}] {item['question']}\n -> SQL: {item['sql']}\n")
