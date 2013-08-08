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
  
  def ajaxGet[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    get(path){
      form.validate("", params) match {
        case Nil    => action(form.convert("", params))
        case errors => {
          status = 400
          contentType = "application/json"
          toJson(errors)
        }
      }
    }    
  }
  
  def ajaxPost[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    post(path){
      form.validate("", params) match {
        case Nil    => action(form.convert("", params))
        case errors => {
          status = 400
          contentType = "application/json"
          toJson(errors)
        }
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