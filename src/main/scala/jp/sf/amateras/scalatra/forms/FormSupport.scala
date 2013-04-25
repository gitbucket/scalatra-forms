package jp.sf.amateras.scalatra.forms

import org.scalatra._
import org.scalatra.servlet.ServletBase

trait FormSupport { self: ServletBase =>
  
  def get[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    get(path){
      withValidation(form, params){ obj: T =>
        action(obj)
      }
    }
  }

  def post[T](path: String, form: MappingValueType[T])(action: T => Any): Route = {
    post(path){
      withValidation(form, params){ obj: T =>
        action(obj)
      }
    }
  }
  
}
