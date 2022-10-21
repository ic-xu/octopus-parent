@用户测试数据
Feature: 第二个测试用咧
  第二个测试用咧

  Scenario Outline: 用户登录场景2
    Given 输入用户名和密码
    When 步骤第一个,获取验证码
    When 输入 <a1> and <a2> 并计算结果
    Then 登录信息 <result>

    Examples:
      | a1 | a2 | result |
      | 1  | 1  | 2      |
      | 2  | 2  | 4      |
      | 3  | 3  | 6      |
      | 4  | 4  | 8      |
      | 5  | 5  | 10     |
