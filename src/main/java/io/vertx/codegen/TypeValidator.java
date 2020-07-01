package io.vertx.codegen;

import io.vertx.codegen.type.ClassKind;
import io.vertx.codegen.type.ParameterizedTypeInfo;
import io.vertx.codegen.type.TypeInfo;
import io.vertx.codegen.type.TypeVariableInfo;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates codegen types.
 */
class TypeValidator {

  static void validateParamType(ExecutableElement elem, TypeInfo typeInfo, boolean allowAnyJavaType) {
    if (isValidNonCallableType(elem, typeInfo, true, false, true, allowAnyJavaType)) {
      return;
    }
    if (isValidClassTypeParam(elem, typeInfo)) {
      return;
    }
    if (isValidHandlerType(elem, typeInfo, allowAnyJavaType)) {
      return;
    }
    if (isValidFunctionType(elem, typeInfo, allowAnyJavaType)) {
      return;
    }
    throw new GenException(elem, "type " + typeInfo + " is not legal for use for a parameter in code generation");
  }

  static void validateReturnType(ExecutableElement elem, TypeInfo type, boolean allowAnyJavaType) {
    if (type.isVoid()) {
      return;
    }
    if (isValidNonCallableType(elem, type, false, true, true, allowAnyJavaType)) {
      return;
    }
    if (isValidHandlerType(elem, type, allowAnyJavaType)) {
      return;
    }
    throw new GenException(elem, "type " + type + " is not legal for use for a return type in code generation");
  }

  static void validateConstantType(VariableElement elem, TypeInfo type, TypeMirror typeMirror, boolean allowAnyJavaType) {
    if (isValidNonCallableType(elem, type, false, true, true, allowAnyJavaType)) {
      return;
    }
    throw new GenException(elem, "type " + type + " is not legal for use for a constant type in code generation");
  }

  private static boolean isValidNonCallableType(Element elem, TypeInfo type, boolean isParam, boolean isReturn, boolean allowParameterized, boolean allowAnyJavaType) {
    if (isValidDataObjectType(elem, type, isParam, isReturn)) {
      return true;
    }
    if (type.getKind() == ClassKind.VOID) {
      return true;
    }
    if (type.getKind().basic) {
      return true;
    }
    if (type.getKind().json) {
      return true;
    }
    if (isValidEnum(type)) {
      return true;
    }
    if (type.getKind() == ClassKind.THROWABLE) {
      return true;
    }
    if (type.isVariable()) {
      return true;
    }
    if (type.getKind() == ClassKind.OBJECT) {
      return true;
    }
    if (isValidVertxGenInterface(elem, type, allowParameterized, allowAnyJavaType)) {
      return true;
    }
    if (isValidOtherType(type, allowAnyJavaType)) {
      return true;
    }
    if (allowParameterized && isValidContainer(elem, type, allowAnyJavaType)) {
      return true;
    }
    return false;
  }

  private static boolean isValidEnum(TypeInfo info) {
    return info.getKind() == ClassKind.ENUM;
  }

  private static boolean isValidClassTypeParam(ExecutableElement elt, TypeInfo type) {
    if (type.getKind() == ClassKind.CLASS_TYPE && type.isParameterized()) {
      ParameterizedTypeInfo parameterized = (ParameterizedTypeInfo) type;
      TypeInfo arg = parameterized.getArg(0);
      if (arg.isVariable()) {
        TypeVariableInfo variable = (TypeVariableInfo) arg;
        for (TypeParameterElement typeParamElt : elt.getTypeParameters()) {
          if (typeParamElt.getSimpleName().toString().equals(variable.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isValidDataObjectType(Element elem, TypeInfo type, boolean isParam, boolean isReturn) {
    if (type.isDataObjectHolder()) {
      if (isParam && !type.getDataObject().isDeserializable()) {
        throw new GenException(elem, "@DataObject type " + type + " does not declare a required deserializable method when used in parameter");
      }
      if (isReturn && !type.getDataObject().isSerializable()) {
        throw new GenException(elem, "@DataObject type " + type + " does not declare a required serializable method when used in return");
      }
      return true;
    }
    return false;
  }

  private static boolean isValidContainer(Element elem, TypeInfo type, boolean allowAnyJavaType) {
    TypeInfo argument = null;
    if (rawTypeIs(type, List.class, Set.class, Map.class)) {
      ParameterizedTypeInfo parameterizedType = (ParameterizedTypeInfo) type;
      if (type.getKind() != ClassKind.MAP) {
        argument = parameterizedType.getArgs().get(0);
      } else if (parameterizedType.getArgs().get(0).getKind() == ClassKind.STRING) { // Only allow Map's with String's for keys
        argument= parameterizedType.getArgs().get(1);
      }
    }
    return argument != null && isValidContainerComponent(elem, argument, allowAnyJavaType);
  }

  private static boolean isValidContainerComponent(Element elem, TypeInfo arg, boolean allowAnyJavaType) {
    return isValidNonCallableType(elem, arg, true, true, false, allowAnyJavaType);
  }

  private static boolean isValidVertxGenTypeArgument(Element elem, TypeInfo arg, boolean allowAnyJavaType) {
    return isValidNonCallableType(elem, arg, false, false, true, allowAnyJavaType);
  }

  private static boolean isValidOtherType(TypeInfo type, boolean allowAnyJavaType) {
    return allowAnyJavaType && type.getKind() == ClassKind.OTHER;
  }

  private static boolean isValidVertxGenInterface(Element elem, TypeInfo type, boolean allowParameterized, boolean allowAnyJavaType) {
    if (type.getKind() == ClassKind.API) {
      if (type.isParameterized()) {
        ParameterizedTypeInfo parameterized = (ParameterizedTypeInfo) type;
        return allowParameterized &&
          parameterized
            .getArgs()
            .stream()
            .noneMatch(arg -> !isValidVertxGenTypeArgument(elem, arg, allowAnyJavaType) || arg.isNullable());
      } else {
        return true;
      }
    }
    return false;
  }

  private static boolean isValidFunctionType(Element elem, TypeInfo typeInfo, boolean allowAnyJavaType) {
    if (typeInfo.getErased().getKind() == ClassKind.FUNCTION) {
      TypeInfo paramType = ((ParameterizedTypeInfo) typeInfo).getArgs().get(0);
      if (isValidCallbackValueType(elem, paramType, allowAnyJavaType)) {
        TypeInfo returnType = ((ParameterizedTypeInfo) typeInfo).getArgs().get(1);
        return isValidNonCallableType(elem, returnType, true, false, true, allowAnyJavaType);
      }
    }
    return false;
  }

  private static boolean isValidHandlerType(Element elem, TypeInfo type, boolean allowAnyJavaType) {
    if (type.getErased().getKind() == ClassKind.HANDLER) {
      TypeInfo eventType = ((ParameterizedTypeInfo) type).getArgs().get(0);
      if (isValidCallbackValueType(elem, eventType, allowAnyJavaType)) {
        return true;
      } else if (eventType.getErased().getKind() == ClassKind.ASYNC_RESULT && !eventType.isNullable()) {
        TypeInfo resultType = ((ParameterizedTypeInfo) eventType).getArgs().get(0);
        if (isValidCallbackValueType(elem, resultType, allowAnyJavaType)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isValidCallbackValueType(Element elem, TypeInfo type, boolean allowAnyJavaType) {
    return isValidNonCallableType(elem, type, false, true, true, allowAnyJavaType);
  }

  private static boolean rawTypeIs(TypeInfo type, Class<?>... classes) {
    if (type instanceof ParameterizedTypeInfo) {
      String rawClassName = type.getRaw().getName();
      for (Class<?> c : classes) {
        if (rawClassName.equals(c.getName())) {
          return true;
        }
      }
    }
    return false;
  }
}
