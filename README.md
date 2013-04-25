scalatra-forms
==============

A library to validate and map request parameters for Scalatra.

Define a form mapping at first. It's a similar to Play2, but scalatra-forms is more flexible.

```scala
import jp.sf.amateras.scalatra.forms._

case class RegisterForm(name: String, description: String)

val form = mapping(
  "name"        -> text(required, maxlength(40)), 
  "description" -> text()
)(RegisterForm.apply)
```

Next, create a servlet (or filter) which extends ScalatraServlet (or ScalatraFilter).
It also mixed in ```jp.sf.amateras.scalatra.forms.FormSupport``` or ```jp.sf.amateras.scalatra.forms.ClientSideValidationFormSupport```.
The object which is mapped request parameters is passed as an argument of action.

```scala
class RegisterServlet extends ScalatraServlet with ClientSideValidationFormSupport {
  post("/register", form) { form: RegisterForm =>
    ...
  }
}
```

In the HTML, add ```validation="true"``` to your form.
scalatra-forms registers a submit event listener to validate form contents.
This listener posts all the form contents to ```FORM_ACTION/validate```.
This action is registered by scalatra-forms automatically to validate form contents.
It returns validation results as JSON.

In the client side, scalatra-forms puts error messages into ```span#error-FIELD_NAME```.

```scala
<form method="POST" action="/register" validation="true">
  Name: <input type="name" type="text">
  <span class="error" id="error-name"></span>
  <br/>
  Description: <input type="description" type="text">
  <span class="error" id="error-description"></span>
  <br/>
  <input type="submit" value="Register"/>
</form>
```

Release Notes
--------

# 0.0.1 - IN DEVELOPMENT

* This is the first public release.