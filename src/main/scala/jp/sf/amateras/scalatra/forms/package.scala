package jp.sf.amateras.scalatra

import org.scalatra.i18n.Messages
import org.json4s._
import org.json4s.jackson._
import java.util.Locale

package object forms {
    
  /**
   * Runs form validation before action.
   * If there are validation error, action is not invoked and this method throws RuntimeException.
   * 
   * @param mapping the mapping definition
   * @param params the request parameters
   * @param action the action
   * @return the result of action
   */
  def withValidation[T](mapping: MappingValueType[T], params: Map[String, String], messages: Messages)(action: T => Any): Any = {
    mapping.validate("", params, messages).isEmpty match {
      case true  => action(mapping.convert("", params, messages))
      case false => throw new RuntimeException("Invalid Request") // TODO show error page?
    }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // ValueTypes
  
  trait ValueType[T] {
    
    def convert(name: String, params: Map[String, String], messages: Messages): T
    
    def validate(name: String, params: Map[String, String], messages: Messages): Seq[(String, String)]
    
    def verifying(validator: (T, Map[String, String]) => Seq[(String, String)]): ValueType[T] = 
      new VerifyingValueType(this, validator)
    
    def verifying(validator: (T) => Seq[(String, String)]): ValueType[T] = 
      new VerifyingValueType(this, (value: T, params: Map[String, String]) => validator(value))
    
  }
  
  /**
   * The base class for the single field ValueTypes.
   */
  abstract class SingleValueType[T](constraints: Constraint*) extends ValueType[T]{
    
    def convert(name: String, params: Map[String, String], messages: Messages): T = 
      convert(params.get(name).orNull, messages)
    
    def convert(value: String, messages: Messages): T
    
    def validate(name: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = 
      validate(name, params.get(name).orNull, params, messages)
    
    def validate(name: String, value: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = 
      validaterec(name, value, params, Seq(constraints: _*), messages)
    
    @scala.annotation.tailrec
    private def validaterec(name: String, value: String, params: Map[String, String], 
                             constraints: Seq[Constraint], messages: Messages): Seq[(String, String)] = {
      constraints match {
        case (x :: rest) => x.validate(name, value, params, messages) match {
          case Some(message) => Seq(name -> message)
          case None          => validaterec(name, value, params, rest, messages)
        }
        case _ => Nil
      }
    }
    
  }
  
  /**
   * ValueType wrapper to verify the converted value.
   * An instance of this class is returned from only [[jp.sf.amateras.scalatra.forms.ValueType#verifying]].
   * 
   * @param valueType the wrapped ValueType
   * @param validator the function which verifies the converted value
   */
  private class VerifyingValueType[T](valueType: ValueType[T], 
      validator: (T, Map[String, String]) => Seq[(String, String)]) extends ValueType[T] {
    
    def convert(name: String, params: Map[String, String], messages: Messages): T = valueType.convert(name, params, messages)
        
    def validate(name: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = {
      val result = valueType.validate(name, params, messages)
      if(result.isEmpty){
        validator(convert(name, params, messages), params)
      } else {
        result
      }
    }
  }
  
  /**
   * The base class for the object field ValueTypes.
   */
  abstract class MappingValueType[T] extends ValueType[T]{

    def fields: Seq[(String, ValueType[_])]
    
    def validate(name: String, params: Map[String, String], messages: Messages): Seq[(String, String)] =
      fields.map { case (fieldName, valueType) => 
        valueType.validate((if(name.isEmpty) fieldName else name + "." + fieldName), params, messages)
      }.flatten
    
    def validateAsJSON(params: Map[String, String], messages: Messages): JObject = toJson(validate("", params, messages))
    
  }
  
  /**
   * Converts errors to JSON.
   */
  protected [forms] def toJson(errors: Seq[(String, String)]): JObject =
    JObject(errors.map { case (key, value) =>
      JField(key, JString(value))
    }.toList)    
  
  /**
   * ValueType for the String property.
   */
  def text(constraints: Constraint*): SingleValueType[String] = new SingleValueType[String](constraints: _*){
    def convert(value: String, messages: Messages): String = value
  }
  
  /**
   * ValueType for the Boolean property.
   */
  def boolean(constraints: Constraint*): SingleValueType[Boolean] = new SingleValueType[Boolean](constraints: _*){
    def convert(value: String, messages: Messages): Boolean = value match {
      case null|"false"|"FALSE" => false
      case _ => true
    }
  }
  
  /**
   * ValueType for the Int property.
   */
  def number(constraints: Constraint*): SingleValueType[Int] = new SingleValueType[Int](constraints: _*){
    
    def convert(value: String, messages: Messages): Int = value match {
      case null|"" => 0
      case x => x.toInt
    }
    
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = {
      try {
        value.toInt
        super.validate(name, value, params, messages)
      } catch {
        case e: NumberFormatException => Seq(name -> messages("error.number").format(name))
      }
    }
  }
  
  /**
   * ValueType for the Double property.
   */
  def double(constraints: Constraint*): SingleValueType[Double] = new SingleValueType[Double](constraints: _*){
    
    def convert(value: String, messages: Messages): Double = value match {
      case null|"" => 0d
      case x => x.toDouble
    }
    
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = {
      try {
        value.toDouble
        super.validate(name, value, params, messages)
      } catch {
        case e: NumberFormatException => Seq(name -> messages("error.number").format(name))
      }
    }
  }
  
  /**
   * ValueType for the java.util.Date property.
   */
  def date(pattern: String, constraints: Constraint*): SingleValueType[java.util.Date] = 
    new SingleValueType[java.util.Date]((datePattern(pattern) +: constraints): _*){
      def convert(value: String, messages: Messages): java.util.Date = new java.text.SimpleDateFormat(pattern).parse(value)
    }
  
  
  def mapping[T, P1](f1: (String, ValueType[P1]))(factory: (P1) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages))
  }
  
  def mapping[T, P1, P2](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]))(factory: (P1, P2) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages))
  }

  def mapping[T, P1, P2, P3](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]))(factory: (P1, P2, P3) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]))(factory: (P1, P2, P3, P4) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]))(factory: (P1, P2, P3, P4, P5) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]))(factory: (P1, P2, P3, P4, P5, P6) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]))(factory: (P1, P2, P3, P4, P5, P6, P7) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages), p(f15, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages), p(f15, name, params, messages), p(f16, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages), p(f15, name, params, messages), p(f16, name, params, messages), p(f17, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages), p(f15, name, params, messages), p(f16, name, params, messages), p(f17, name, params, messages), p(f18, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages), p(f15, name, params, messages), p(f16, name, params, messages), p(f17, name, params, messages), p(f18, name, params, messages), p(f19, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages), p(f15, name, params, messages), p(f16, name, params, messages), p(f17, name, params, messages), p(f18, name, params, messages), p(f19, name, params, messages), p(f20, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]), f21: (String, ValueType[P21]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages), p(f15, name, params, messages), p(f16, name, params, messages), p(f17, name, params, messages), p(f18, name, params, messages), p(f19, name, params, messages), p(f20, name, params, messages), p(f21, name, params, messages))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]), f21: (String, ValueType[P21]), f22: (String, ValueType[P22]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21, f22)
    def convert(name: String, params: Map[String, String], messages: Messages) = factory(p(f1, name, params, messages), p(f2, name, params, messages), p(f3, name, params, messages), p(f4, name, params, messages), p(f5, name, params, messages), p(f6, name, params, messages), p(f7, name, params, messages), p(f8, name, params, messages), p(f9, name, params, messages), p(f10, name, params, messages), p(f11, name, params, messages), p(f12, name, params, messages), p(f13, name, params, messages), p(f14, name, params, messages), p(f15, name, params, messages), p(f16, name, params, messages), p(f17, name, params, messages), p(f18, name, params, messages), p(f19, name, params, messages), p(f20, name, params, messages), p(f21, name, params, messages), p(f22, name, params, messages))
  }
  
  private def p[T](field: (String, ValueType[T]), name: String, params: Map[String, String], messages: Messages): T = 
    field match { case (fieldName, valueType) =>
      valueType.convert(if(name.isEmpty) fieldName else name + "." + fieldName, params, messages)
    }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // ValueType wrappers to provide additional features.
  
  private val pattern = "(.+?)\\[([0-9]+)\\]\\[(.+?)\\]".r
  
  /**
   * ValueType for the List property.
   * Parameter name must be "name[index][subName]".
   */
  def list[T](mapping: MappingValueType[T]): ValueType[List[T]] = new ValueType[List[T]](){

    private def extractListParams(params: Map[String, String]) = {
      params.flatMap { case (key, value) =>
        key match {
          case pattern(_, i, s) => Some((i.toInt, s, value))
          case _ => None
        }
      }
      .groupBy { case (i, key, value) => i }
      .map     { case (i, values) =>
        (i -> values.map { case (_, key, value) =>
          key -> value
        }.toMap)
      }
    }

    override def convert(name: String, params: Map[String, String], messages: Messages): List[T] = {
      val listParams = extractListParams(params)
      val max = if(listParams.isEmpty) -1 else listParams.map(_._1).max

      (for(i <- 0 to max) yield {
        val rowParams = listParams.getOrElse(i, Map.empty[String, String])
        mapping.convert("", rowParams, messages)
      }).toList
    }

    def validate(name: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = {
      val listParams = extractListParams(params)
      val max = if(listParams.isEmpty) -1 else listParams.map(_._1).max

      (for(i <- 0 to max) yield {
        val rowParams = listParams.getOrElse(i, Map.empty[String, String])
        mapping.validate("", rowParams, messages).map { case (key, message) =>
          (key + "_" + i, message)
        }
      }).flatten
    }
  }  
  
  /**
   * ValueType wrapper for the optional property.
   */
  def optional[T](valueType: SingleValueType[T]): SingleValueType[Option[T]] = new SingleValueType[Option[T]](){
    def convert(value: String, messages: Messages): Option[T] = 
      if(value == null || value.isEmpty) None else Some(valueType.convert(value, messages))
      
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = 
      if(value == null || value.isEmpty) Nil else valueType.validate(name, value, params, messages)
  }
  
  /**
   * ValueType wrapper for the optional mapping property.
   */
  def optional[T](condition: (Map[String, String]) => Boolean, valueType: MappingValueType[T]): ValueType[Option[T]] = new ValueType[Option[T]](){
    override def convert(name: String, params: Map[String, String], messages: Messages): Option[T] = 
      if(condition(params)) Some(valueType.convert(name, params, messages)) else None
      
    override def validate(name: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = 
      if(condition(params)) valueType.validate(name, params, messages) else Nil
  }
  
  /**
   * ValueType wrapper for the optional property which is available if checkbox is checked.
   */
  def optionalIfNotChecked[T](checkboxName: String, valueType: MappingValueType[T]): ValueType[Option[T]] = 
    optional({ params => boolean().convert(checkboxName, params, null) }, valueType)
  
  /**
   * ValueType wrapper for the optional property which is required if condition is true.
   */
  def optionalRequired[T](condition: (Map[String, String]) => Boolean, 
                          valueType: SingleValueType[T]): SingleValueType[Option[T]] = new SingleValueType[Option[T]](){
    def convert(value: String, messages: Messages): Option[T] = 
      if(value == null || value.isEmpty) None else Some(valueType.convert(value, messages))
      
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Seq[(String, String)] =
      required.validate(name, value, messages) match {
        case Some(error) if(condition(params)) => Seq(name -> error)
        case Some(error) => Nil
        case None => valueType.validate(name, value, params, messages)
      }
  }
  
  /**
   * ValueType wrapper for the optional property which is required if checkbox is checked.
   */
  def optionalRequiredIfChecked[T](checkboxName: String, valueType: SingleValueType[T]): SingleValueType[Option[T]] = 
    optionalRequired(_.get(checkboxName).orNull match {
      case null|"false"|"FALSE" => false
      case _                    => true
    }, valueType)
  
  /**
   * ValueType wrapper to trim a parameter.
   * 
   * {{{
   * val form = mapping(
   *   "name" -> trim(text(required)),
   *   "mail" -> trim(text(required))
   * )
   * }}}
   */
  def trim[T](valueType: SingleValueType[T]): SingleValueType[T] = new SingleValueType[T](){
    
    def convert(value: String, messages: Messages): T = valueType.convert(trim(value), messages)
    
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = 
      valueType.validate(name, trim(value), params, messages)
    
    private def trim(value: String): String = if(value == null) null else value.trim
  }
  
  /**
   * ValueType wrapper to specified a property name which is used in the error message. 
   * 
   * {{{
   * val form = trim(mapping(
   *   "name" -> label("User name"   , text(required)),
   *   "mail" -> label("Mail address", text(required))
   * ))
   * }}}
    */
  def label[T](label: String, valueType: SingleValueType[T]): SingleValueType[T] = new SingleValueType[T](){
    
    def convert(value: String, messages: Messages): T = valueType.convert(value, messages)
    
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = 
      valueType.validate(label, value, params, messages).map { case (label, message) => name -> message }
    
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constraints
  
  trait Constraint {
    
    def validate(name: String, value: String, params: Map[String, String], messages: Messages): Option[String] = validate(name, value, messages)
    
    def validate(name: String, value: String, messages: Messages): Option[String] = None
    
  }
  
  def required: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      if(value == null || value.isEmpty) Some(messages("error.required").format(name)) else None
    }
  }
  
  def required(message: String): Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = 
      if(value == null || value.isEmpty) Some(message) else None
  }
  
  def maxlength(length: Int): Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(value != null && value.length > length) Some(messages("error.maxlength").format(name, length)) else None
  }
  
  def minlength(length: Int): Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(value != null && value.length < length) Some(messages("error.minlength").format(name, length)) else None
  }
  
  def oneOf(values: Seq[String], message: String = ""): Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = 
      if(value != null && !values.contains(value)){
        if(message.isEmpty) Some(messages("error.oneOf").format(name, values.map("'" + _ + "'").mkString(", "))) else Some(message)
      } else None
  }
  
  def pattern(pattern: String, message: String = ""): Constraint = new Constraint {
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(value != null && !value.matches("^" + pattern + "$")){
        if(message.isEmpty) Some(messages("error.pattern").format(name, pattern)) else Some(message)
      } else None
  }
  
  def datePattern(pattern: String, message: String = ""): Constraint = new Constraint {
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(value != null && value.nonEmpty){
        try {
          new java.text.SimpleDateFormat(pattern).parse(value)
          None
        } catch {
          case e: java.text.ParseException => 
            if(message.isEmpty) Some(messages("error.datePattern").format(name, pattern)) else Some(message)
        }
      } else None
  }  
  
}