package jp.sf.amateras.scalatra.forms

import org.scalatra._
import org.scalatra.json._
import org.scalatra.i18n.I18nSupport
import org.scalatra.servlet.ServletBase
import java.util.ResourceBundle

trait ClientSideValidationFormSupport { self: ServletBase with JacksonJsonSupport with I18nSupport =>

  def get[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    get(path){
      withValidation(form, params, messages){ obj: T =>
        action(obj)
      }
    }
  }

  def post[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    post(path){
      withValidation(form, params, messages){ obj: T =>
        action(obj)
      }
    }
  }

  def put[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    put(path){
      withValidation(form, params, messages){ obj: T =>
        action(obj)
      }
    }
  }

  def delete[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    delete(path){
      withValidation(form, params, messages){ obj: T =>
        action(obj)
      }
    }
  }

  def ajaxGet[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    get(path){
      form.validate("", params, messages) match {
        case Nil    => action(form.convert("", params, messages))
        case errors => {
          status = 400
          contentType = "application/json"
          toJson(errors)
        }
      }
    }
  }

  def ajaxPost[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    post(path){
      form.validate("", params, messages) match {
        case Nil    => action(form.convert("", params, messages))
        case errors => {
          status = 400
          contentType = "application/json"
          toJson(errors)
        }
      }
    }
  }

  def ajaxDelete[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    delete(path){
      form.validate("", params, messages) match {
        case Nil    => action(form.convert("", params, messages))
        case errors => {
          status = 400
          contentType = "application/json"
          toJson(errors)
        }
      }
    }
  }

  def ajaxPut[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    put(path){
      form.validate("", params, messages) match {
        case Nil    => action(form.convert("", params, messages))
        case errors => {
          status = 400
          contentType = "application/json"
          toJson(errors)
        }
      }
    }
  }

  private def registerValidate[T](path: String, form: ValueType[T]) = {
    post(path.replaceFirst("/$", "") + "/validate"){
      contentType = "application/json"
      form.validateAsJSON(params, messages)
    }
  }

}