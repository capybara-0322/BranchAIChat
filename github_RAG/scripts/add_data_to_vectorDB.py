import os
import yaml
from pathlib import Path
from langchain_community.document_loaders import WebBaseLoader, TextLoader
from langchain_community.vectorstores import Chroma
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_openai import OpenAIEmbeddings
from bs4 import BeautifulSoup
from utils.get_env_var import get_env_var


def load_config(config_path) -> dict:
    """加载 YAML 配置文件"""
    if not os.path.exists(config_path):
        raise FileNotFoundError(f"找不到配置文件: {config_path}")
    with open(config_path, "r", encoding="utf-8") as f:
        config = yaml.safe_load(f)
    return config


def store_to_chroma(source: str, config: dict, collection_name: str = "default_collection"):
    """
    将本地文件或网页内容加载并存入 Chroma 向量数据库。
    参数：
        source (str): 可以是本地文件路径或网页 URL。
        config (dict): 配置参数，包含 chroma_db_path、embedding_model 等。
        collection_name (str): 数据集合名称。
    """

    chroma_db_path = config.get("chroma_db_path", "./chroma_db")
    embedding_model = config.get("embedding_model", "sentence-transformers/all-MiniLM-L6-v2")

    # 判断是网页还是本地文件
    if source.startswith("http"):
        print(f"从网络加载内容: {source}")
        loader = WebBaseLoader(web_paths=(source,))
        docs = loader.load()
    else:
        file_path = Path(source)
        if not file_path.exists():
            raise FileNotFoundError(f"找不到文件: {file_path}")
        print(f"从本地文件加载: {file_path}")
        loader = TextLoader(str(file_path), encoding="utf-8")
        docs = loader.load()

    # 文本切分
    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=500,
        chunk_overlap=200
    )
    splits = text_splitter.split_documents(docs)

    # 嵌入并保存到 Chroma
    print(f"正在使用 Embedding 模型：{embedding_model}")
    embeddings = OpenAIEmbeddings(model=embedding_model)

    print("正在生成向量并存储到 Chroma 数据库 ...")
    vectorstore = Chroma.from_documents(
        documents=splits,
        embedding=embeddings,
        persist_directory=chroma_db_path,
        collection_name=collection_name
    )

    vectorstore.persist()
    print(f"数据成功存入 Chroma 数据库！路径: {chroma_db_path}")


if __name__ == "__main__":
    BASE_DIR = Path(__file__).resolve().parent.parent  # add_data_to_vectorDB.py 的上上一层：项目根目录
    config_path = BASE_DIR / "config.yaml"
    # 加载配置文件
    config = load_config(config_path)

    # 从配置中获取路径
    chroma_path = config.get("chroma_db_path", "./chroma_db")
    print(f"数据库存储路径: {chroma_path}")

    # 用户输入
    source = input("请输入本地文件路径或网页 URL: ").strip()
    store_to_chroma(source, config, collection_name="Agent")
