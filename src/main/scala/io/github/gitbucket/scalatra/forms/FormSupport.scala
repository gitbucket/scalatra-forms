package io.github.gitbucket.scalatra.forms

import org.scalatra._
import org.scalatra.i18n._
import org.scalatra.servlet.ServletBase
import java.util.ResourceBundle

trait FormSupport { self: ServletBase with I18nSupport =>

  def get[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    get(path){
      val paramMap = params.toSeq.toMap
      withValidation(form, paramMap, messages){ obj: T =>
        action(obj)
      }
    }
  }

  def post[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    post(path){
      val paramMap = params.toSeq.toMap
      withValidation(form, paramMap, messages){ obj: T =>
        action(obj)
      }
    }
  }

}
