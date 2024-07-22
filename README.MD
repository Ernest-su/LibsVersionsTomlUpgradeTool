# 更新libs.versions.toml 文件小工具
把`libs.versions.toml`文件放到项目根目录下，
运行后，会从https://repo1.maven.org和https://maven.google.com 检查有没有版本更新，并更新`libs.versions.toml`文件

## 注意
请求google仓库有的地方需要翻墙，代码的okhttp配置了本地的127.0.0.1的代理，如不需要可注释掉。