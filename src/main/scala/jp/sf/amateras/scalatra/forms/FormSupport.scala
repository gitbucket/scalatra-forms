package jp.sf.amateras.scalatra.forms

import org.scalatra._
import org.scalatra.i18n.I18nSupport
import org.scalatra.servlet.ServletBase
import java.util.ResourceBundle

trait FormSupport { self: ServletBase with I18nSupport =>
  
  def get[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    get(path){
      withValidation(form, params, getBundle(locale)){ obj: T =>
        action(obj)
      }
    }
  }

  def post[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    post(path){
      withValidation(form, params, getBundle(locale)){ obj: T =>
        action(obj)
      }
    }
  }
  
}
