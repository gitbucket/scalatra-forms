scalatra-forms
==============

A library to validate and map request parameters for Scalatra.

Getting Started
--------

At first, add the following dependency into your build.sbt to use scalatra-forms.

```scala
resolvers += "amateras-repo" at "http://amateras.sourceforge.jp/mvn/"

libraryDependencies += "jp.sf.amateras" %% "scalatra-forms" % "0.0.6"
```

Next, add ```ValidationJavaScriptProvider``` to Bootstrap of your Scalatra application.

```scala
import jp.sf.amateras.scalatra.forms._

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    ...
    context.mount(new ValidationJavaScriptProvider, "/assets/js/*")
    ...
  }
}
```

scalatra-forms is now ready.

Define a form mapping. It's a similar to Play2, but scalatra-forms is more flexible.

```scala
import jp.sf.amateras.scalatra.forms._

case class RegisterForm(name: String, description: String)

val form = mapping(
  "name"        -> text(required, maxlength(40)), 
  "description" -> text()
)(RegisterForm.apply)
```

Next, create a servlet (or filter) which extends ScalatraServlet (or ScalatraFilter).
It also mixed in ```FormSupport``` or ```ClientSideValidationFormSupport```.
The object which is mapped request parameters is passed as an argument of action.

```scala
class RegisterServlet extends ScalatraServlet with ClientSideValidationFormSupport {
  post("/register", form) { form: RegisterForm =>
    ...
  }
}
```

In the HTML, you have to do two things below.

- Add ```<script>``` to import jQuery which is required by validation.js
- Add ```<script>``` to import validation.js which helps client side validation provided by ```ValidationJavaScriptProvider```
- Add ```validation="true"``` to your ```<form>```

scalatra-forms registers a submit event listener to validate form contents.
This listener posts all the form contents to ```FORM_ACTION/validate```.
This action is registered by scalatra-forms automatically to validate form contents.
It returns validation results as JSON.

In the client side, scalatra-forms puts error messages into ```span#error-FIELD_NAME```.

```scala
<script src="http://code.jquery.com/jquery-2.0.3.min.js"></script>
<script src="/assets/js/validation.js"></script>
...
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

Custom Validation
--------

You can create custom ```Constraint```.

```scala
def identifier: Constraint = new Constraint(){
  override def validate(name: String, value: String): Option[String] =
    if(!value.matches("^[a-zA-Z0-9\\-_]+$")){
      Some(s"${name} contains invalid character.")
    } else {
      None
    }
}

val form = mapping(
  "name"        -> text(required, identifier), 
  "description" -> text()
)(RegisterForm.apply)
```

You can also create multi field validator by overriding ```validate(String, String, Map[String, String])```.
It's possible to look up other field value via ```params```.

Other way to create multi field validator is calling ```verifying``` for mapping.
You can give the function yo validate the mapped case class. This function takes the mapped value and returns 
```Seq[(String, String)]``` which contains errors or ```Nil```.

```scala
val form = mapping(
  "reason"      -> number(required), 
  "description" -> optional(text)
)(ReasonForm.apply).verifying { value =>
  if(value.reason == 4 && value.descripsion){
    Seq("description" -> "If reason is 'Other' then description is required.")
  } else {
    Nil
  }
}
```

Ajax
--------

For the Ajax action, use ```ajaxGet``` or ```ajaxPost``` instead of ```get``` or ```post```.
Actions which defined by ```ajaxGet``` or ```ajaxPost``` return validation result as JSON response.

```scala
class RegisterServlet extends ScalatraServlet with ClientSideValidationFormSupport {
  ajaxPost("/register", form) { form: RegisterForm =>
    ...
  }
}
```

In the client side, you can render error messages using ```displayErrors()```. 

```js
$('#register').click(function(e){
  $.ajax($(this).attr('action'), {
    type: 'POST',
    data: {
      name       : $('#name').val(),
      description: $('#description').val()
    }
  })
  .done(function(data){
    $('#result').text('Registered!');
  })
  .fail(function(data, status){
    displayErrors($.parseJSON(data.responseText));
  });
});
```

Release Notes
--------
### 0.0.9 - 6 Dec 2013

* Add ```dummy``` value type.

### 0.0.8 - 14 Nov 2013

* Fix nested property handling problem.
* Fix ``verifying()`` for ```MappingValueType``` and remove ```MappingConstraint``` instead of it.
* Add ``length``` constraint.

### 0.0.7 - 12 Nov 2013

* Add ```MappingConstraint``` to validate converted object by ```MappingValueType```.

### 0.0.6 - 8 Nov 2013

* Add ```list()``` mapping for ```SingleValueType```.
* ```ValidationJavaScriptProvider``` adds Content-Type header for validation.js.
* Fix to run parent validations before number checking.

### 0.0.5 - 3 Nov 2013

* Add ```oneOf()``` constraint which checks whether the value is one of specified strings.
* Fix to retrieve error message for ```number()``` and ```double()``` from ResourceBundle.

### 0.0.4 - 2 Nov 2013

* Add ```double()``` and ```date()``` mapping.
* Add I18N support for error messages.

### 0.0.3 - 18 Oct 2013

* Add ```ValidationJavaScriptProvoider```.
* Add ```list()``` mapping for List property.

### 0.0.2 - 10 Aug 2013

* Improved nested property support.
* Add ```validate(String, String, Map[String, String])``` to ```Constraint```. 
  It makes possible to access other parameter in single field validation.
* Add ```verify()``` to ```MappingValueType``` which validates the mapped instance.

### 0.0.1 - 04 Aug 2013

* This is the first public release.
