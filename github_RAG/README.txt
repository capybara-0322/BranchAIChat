### 基于langchain实现的RAG部分

# 使用方法
配置环境变量：OPENAI_API_KEY
配置关系型数据库：见配置部分
启用main.py可直接开启基于RAG的问答
启用RagPromptServer.py会在本地127.0.0.1:7000端口开启一个服务，用于将问题进行处理后进行数据检索，返回组装完毕的prompt，用POST方式访问/generate_prompt路径，请求体传入一个JSON对象，"question"字段为问题，传回"prompt"字段为查询组装后的prompt

## 配置方式

# 配置RAG路由的方式及种类
在main.py和RagPromptServer.py中更改route_type type_description字段，若需进行SQL数据生成、向量数据库元数据生成等，同步更改query_constructor.py。
更改retriever.py，增加不同路由的处理方式

# 配置关系型数据库内容
根据数据库需要，更改query_constructor.py

# 其他有关配置
更改config.yaml


