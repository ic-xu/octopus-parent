package io.test;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assertions;

public class ExampleDefinitions {
    int temp;

    @Given("输入用户名和密码")
    public void test() {
        // Write code here that turns the phrase above into concrete actions

    }

    @When("步骤第一个,获取验证码")
    public void get() {
        // Write code here that turns the phrase above into concrete actions
    }

    @When("输入 {int} and {int} 并计算结果")
    public int cucus(Integer int1, Integer int2) {
        // Write code here that turns the phrase above into concrete actions
        temp = int1 + int2;
        return temp;
    }

    @Then("登录信息 {int}")
    public void login(Integer int1) {
        // Write code here that turns the phrase above into concrete actions
       Assertions.assertEquals(temp,int1);
    }

}
