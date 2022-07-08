package io.octopus.kernel.kernel.message;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 16:07
 */
public interface IPackageId {


  /**
   * 长id,返回值是Long
   *
   * @return
   */
  Long longId();


  /**
   * 短Id ，返回值是0～65535
   *
   * @return
   */
  Short shortId();

}
