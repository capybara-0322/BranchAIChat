# 文件名：get_env_var.py
import os


def get_env_var(var_name: str) -> str:
    """
    根据变量名获取系统环境变量。

    参数：
        var_name (str): 环境变量名

    返回：
        str: 对应的环境变量值；若不存在则返回提示信息
    """
    value = os.environ.get(var_name)
    if value is not None:
        return value
    else:
        return f"环境变量 '{var_name}' 不存在。"


# 示例：当直接运行此文件时执行测试
if __name__ == "__main__":
    var = input("请输入要查询的环境变量名：")
    result = get_env_var(var)
    print(result)
