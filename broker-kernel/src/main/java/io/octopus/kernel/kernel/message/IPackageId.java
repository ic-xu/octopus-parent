package io.octopus.kernel.kernel.message;

import java.io.IOException;

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
  Long messageId();


  /**
   * 短Id ，返回值是0～65535
   *
   * @return
   */
  Short packageId();



  Integer getSize() throws IOException;

}
