package jp.sf.amateras.scalatra.forms

import org.scalatra._
import org.scalatra.i18n._
import org.scalatra.servlet.ServletBase
import java.util.ResourceBundle

trait FormSupport { self: ServletBase with I18nSupport =>

  def get[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    get(path){
      withValidation(form, params, messages){ obj: T =>
        action(obj)
      }
    }
  }

  def post[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    post(path){
      withValidation(form, params, messages){ obj: T =>
        action(obj)
      }
    }
  }

}
