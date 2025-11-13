from typing import List, Dict
from langchain_community.vectorstores import Chroma
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_openai import OpenAIEmbeddings
import mysql.connector
import yaml


class Retriever:
    def __init__(self, config: Dict):
        """
        初始化Retriever，根据config设置向量数据库、MySQL连接等参数。
        """

        self.config = config

        # 选择embedding模型
        embedding_model = config.get("embedding_model", "sentence-transformers/all-MiniLM-L6-v2")
        self.embedding = OpenAIEmbeddings(model=embedding_model)


        # 向量数据库路径
        self.chroma_path = config.get("chroma_db_path", "../database/chroma database/vector_data_1")

        # MySQL配置
        mysql_host = config.get("mysql_host", "localhost")
        if ":" in mysql_host:
            mysql_host, mysql_port = mysql_host.split(":")
            mysql_port = int(mysql_port)
        else:
            mysql_port = 3306

        self.mysql_config = {
            "host": mysql_host,
            "port": mysql_port,
            "user": config.get("mysql_user", "root"),
            "password": config.get("mysql_pass", ""),
            "database": config.get("mysql_db", "rag")
        }

        # 检索数量
        self.top_k = config.get("retrieval_top_k", 3)

    def retrieve(self, expanded_queries: List[Dict]) -> List[Dict]:
        """
        主检索函数，根据route_type进行不同处理。
        """
        results = []

        for item in expanded_queries:
            route_type = item.get("route_type")
            sql = item.get("sql", "")
            question = item.get("question", "")

            if route_type == "RAG_knowledge":
                item["vector_retrieve"] = self._retrieve_from_chroma(question)

            elif route_type == "student_information":
                if sql.strip():
                    item["relational_retrieve"] = self._retrieve_from_mysql(sql)
                else:
                    item["relational_retrieve"] = []

            elif route_type == "general_knowledge":
                # 不处理
                pass

            results.append(item)

        return results

    def _retrieve_from_chroma(self, query: str) -> List[str]:
        """
        从Chroma向量数据库检索相似文档。
        """
        try:
            vectorstore = Chroma(
                collection_name="Agent",
                persist_directory=self.chroma_path,
                embedding_function=self.embedding
            )
            docs = vectorstore.similarity_search(query, k=self.top_k)
            return [doc.page_content for doc in docs]
        except Exception as e:
            print(f"[Chroma 检索错误]: {e}")
            return []

    def _retrieve_from_mysql(self, sql: str) -> List[Dict]:
        """
        执行MySQL查询并返回结果。
        """
        try:
            conn = mysql.connector.connect(**self.mysql_config)
            cursor = conn.cursor(dictionary=True)
            cursor.execute(sql)
            results = cursor.fetchall()
            cursor.close()
            conn.close()
            return results
        except Exception as e:
            print(f"[MySQL 查询错误]: {e}")
            return []


# ========== 测试示例 ==========


def load_config(path="../config.yaml"):
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


if __name__ == "__main__":
    # 模拟已解析的 YAML 配置
    config = load_config()

    test_data = [
        {
            "question": "what are the limitations of RAG and LLM",
            "route_type": "RAG_knowledge",
            "sql": ""
        },
        {
            "question": "Find students with math_score > 90",
            "route_type": "student_information",
            "sql": "SELECT name, math_score FROM students WHERE math_score > 90;"
        },
        {
            "question": "Who is the president of France?",
            "route_type": "general_knowledge",
            "sql": ""
        }
    ]

    retriever = Retriever(config)
    results = retriever.retrieve(test_data)
    for r in results:
        print(r)
