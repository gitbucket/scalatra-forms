package jp.sf.amateras.scalatra.forms

import org.scalatra._
import org.scalatra.json._
import org.scalatra.servlet.ServletBase

trait ClientSideValidationFormSupport { self: ServletBase with JacksonJsonSupport =>
  
  def get[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    get(path){
      withValidation(form, params){ obj: T =>
        action(obj)
      }
    }
  }

  def post[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    post(path){
      withValidation(form, params){ obj: T =>
        action(obj)
      }
    }
  }
  
  private def registerValidate[T](path: String, form: MappingValueType[T]) = {
    post(path.replaceFirst("/$", "") + "/validate"){
      contentType = "application/json"
      form.validateAsJSON(params)
    }
  }
  
}