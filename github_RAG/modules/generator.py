from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from typing import Dict

class Generator:
    def __init__(self, config: Dict):
        """
        初始化Generator类。
        从config中读取生成模型（llm_model）、温度等参数。
        """
        self.config = config
        model_name = config.get("llm_model", "gpt-4o-mini")
        temperature = config.get("temperature", 0.2)

        # 初始化LLM
        self.llm = ChatOpenAI(
            model=model_name,
            temperature=temperature
        )

        # 基础提示模板（可以灵活扩展）
        self.prompt_template = ChatPromptTemplate.from_template("{prompt}")

    def generate(self, prompt: str) -> Dict:
        """
        主生成方法：传入拼接好的prompt，返回LLM的回答。
        """
        try:
            chain = self.prompt_template | self.llm
            response = chain.invoke({"prompt": prompt})
            answer = response.content.strip()

            return {
                "final_answer": answer,
                "used_model": self.config.get("llm_model", "unknown"),
                "raw_prompt": prompt  # 可选字段，便于调试
            }

        except Exception as e:
            print(f"[LLM生成错误]: {e}")
            return {
                "final_answer": "",
                "used_model": self.config.get("llm_model", "unknown"),
                "error": str(e)
            }


# ========== 测试示例 ==========
if __name__ == "__main__":
    config = {
        "llm_model": "gpt-4o-mini",
        "temperature": 0.3
    }

    # 模拟上一步的PromptConstructor输出
    test_prompt = """You are a reasoning assistant...
[Original Question]
Which students have math_score > 90?

[Related Sub-Questions and Retrieved Results]
Q: Which students have math_score > 90?
Type: student_information
Retrieved Data:
- name: Alice, math_score: 95
- name: Bob, math_score: 92

Now write the final answer in concise natural language:
"""

    generator = Generator(config)
    result = generator.generate(test_prompt)

    print("最终回答：")
    print(result["final_answer"])
    print("\n使用模型：", result["used_model"])
