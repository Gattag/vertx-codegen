package io.vertx.test.codegen.testapi;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface InterfaceWithFluentMethods {

  @Fluent
  InterfaceWithFluentMethods foo(String str);

  @Fluent
  InterfaceWithFluentMethods bar(int i);

}