package jp.sf.amateras.scalatra.forms

import org.scalatra._
import org.scalatra.json._
import org.scalatra.i18n.I18nSupport
import org.scalatra.servlet.ServletBase
import java.util.ResourceBundle

trait ClientSideValidationFormSupport { self: ServletBase with JacksonJsonSupport with I18nSupport =>
  
  def get[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    get(path){
      val bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale(request))
      withValidation(form, params, bundle){ obj: T =>
        action(obj)
      }
    }
  }

  def post[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    post(path){
      val bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale(request))
      withValidation(form, params, bundle){ obj: T =>
        action(obj)
      }
    }
  }
  
  def ajaxGet[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    get(path){
      val bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale(request))
      form.validate("", params, bundle) match {
        case Nil    => action(form.convert("", params, bundle))
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
      val bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale(request))
      form.validate("", params, bundle) match {
        case Nil    => action(form.convert("", params, bundle))
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
      val bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale(request))
      form.validateAsJSON(params, bundle)
    }
  }
  
}