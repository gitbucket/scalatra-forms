package jp.sf.amateras.scalatra

import org.json4s._
import org.json4s.jackson._

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
  def withValidation[T](mapping: MappingValueType[T], params: Map[String, String])(action: T => Any): Any = {
    mapping.validate("", params).isEmpty match {
      case true  => action(mapping.convert("", params))
      case false => throw new RuntimeException("Invalid Request") // TODO show error page?
    }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // ValueTypes
  
  trait ValueType[T] {
    
    def convert(name: String, params: Map[String, String]): T
    
    def validate(name: String, params: Map[String, String]): Seq[(String, String)]
    
    def verifying(validator: (T, Map[String, String]) => Seq[(String, String)]): ValueType[T] = 
      new VerifyingValueType(this, validator)
    
    def verifying(validator: (T) => Seq[(String, String)]): ValueType[T] = 
      new VerifyingValueType(this, (value: T, params: Map[String, String]) => validator(value))
    
  }
  
  /**
   * The base class for the single field ValueTypes.
   */
  abstract class SingleValueType[T](constraints: Constraint*) extends ValueType[T]{
    
    def convert(name: String, params: Map[String, String]): T = 
      convert(params.get(name).orNull)
    
    def convert(value: String): T
    
    def validate(name: String, params: Map[String, String]): Seq[(String, String)] = 
      validate(name, params.get(name).orNull, params)
    
    def validate(name: String, value: String, params: Map[String, String]): Seq[(String, String)] = 
      validaterec(name, value, params, Seq(constraints: _*))
    
    @scala.annotation.tailrec
    private def validaterec(name: String, value: String, params: Map[String, String], 
                             constraints: Seq[Constraint]): Seq[(String, String)] = {
      constraints match {
        case (x :: rest) => x.validate(name, value, params) match {
          case Some(message) => Seq(name -> message)
          case None          => validaterec(name, value, params, rest)
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
    
    def convert(name: String, params: Map[String, String]): T = valueType.convert(name, params)
        
    def validate(name: String, params: Map[String, String]): Seq[(String, String)] = {
      val result = valueType.validate(name, params)
      if(result.isEmpty){
        validator(convert(name, params), params)
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
    
    def validate(name: String, params: Map[String, String]): Seq[(String, String)] =
      fields.map { case (fieldName, valueType) => 
        valueType.validate((if(name.isEmpty) fieldName else name + "." + fieldName), params)
      }.flatten
    
    def validateAsJSON(params: Map[String, String]): JObject = toJson(validate("", params))
    
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
    def convert(value: String): String = value
  }
  
  /**
   * ValueType for the Boolean property.
   */
  def boolean(constraints: Constraint*): SingleValueType[Boolean] = new SingleValueType[Boolean](constraints: _*){
    def convert(value: String): Boolean = value match {
      case null|"false"|"FALSE" => false
      case _ => true
    }
  }
  
  /**
   * ValueType for the Int property.
   */
  def number(constraints: Constraint*): SingleValueType[Int] = new SingleValueType[Int](constraints: _*){
    
    def convert(value: String): Int = value match {
      case null|"" => 0
      case x => x.toInt
    }
    
    override def validate(name: String, value: String, params: Map[String, String]): Seq[(String, String)] = {
      try {
        value.toInt
        super.validate(name, value, params)
      } catch {
        case e: NumberFormatException => Seq(name -> "%s must be a number.".format(name))
      }
    }
  }
  
  def mapping[T, P1](f1: (String, ValueType[P1]))(factory: (P1) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params))
  }
  
  def mapping[T, P1, P2](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]))(factory: (P1, P2) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params))
  }

  def mapping[T, P1, P2, P3](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]))(factory: (P1, P2, P3) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]))(factory: (P1, P2, P3, P4) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]))(factory: (P1, P2, P3, P4, P5) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]))(factory: (P1, P2, P3, P4, P5, P6) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]))(factory: (P1, P2, P3, P4, P5, P6, P7) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name, params), p(f12, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name, params), p(f12, name, params), p(f13, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params), p(f15, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params), p(f15, name, params), p(f16, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params), p(f15, name, params), p(f16, name, params), p(f17, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params), p(f15, name, params), p(f16, name, params), p(f17, name, params), p(f18, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params), p(f15, name, params), p(f16, name, params), p(f17, name, params), p(f18, name, params), p(f19, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params), p(f15, name, params), p(f16, name, params), p(f17, name, params), p(f18, name, params), p(f19, name, params), p(f20, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]), f21: (String, ValueType[P21]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params), p(f15, name, params), p(f16, name, params), p(f17, name, params), p(f18, name, params), p(f19, name, params), p(f20, name, params), p(f21, name, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]), f21: (String, ValueType[P21]), f22: (String, ValueType[P22]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21, f22)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, name, params), p(f2, name, params), p(f3, name, params), p(f4, name, params), p(f5, name, params), p(f6, name, params), p(f7, name, params), p(f8, name, params), p(f9, name, params), p(f10, name, params), p(f11, name,  params), p(f12, name, params), p(f13, name, params), p(f14, name, params), p(f15, name, params), p(f16, name, params), p(f17, name, params), p(f18, name, params), p(f19, name, params), p(f20, name, params), p(f21, name, params), p(f22, name, params))
  }
  
  private def p[T](field: (String, ValueType[T]), name: String, params: Map[String, String]): T = 
    field match { case (fieldName, valueType) =>
      valueType.convert(if(name.isEmpty) fieldName else name + "." + fieldName, params)
    }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // ValueType wrappers to provide additional features.
  
  /**
   * ValueType wrapper for the optional property.
   */
  def optional[T](valueType: SingleValueType[T]): SingleValueType[Option[T]] = new SingleValueType[Option[T]](){
    def convert(value: String): Option[T] = 
      if(value == null || value.isEmpty) None else Some(valueType.convert(value))
      
    override def validate(name: String, value: String, params: Map[String, String]): Seq[(String, String)] = 
      if(value == null || value.isEmpty) Nil else valueType.validate(name, value, params)
  }
  
  /**
   * ValueType wrapper for the optional mapping property.
   */
  def optional[T](condition: (Map[String, String]) => Boolean, valueType: MappingValueType[T]): ValueType[Option[T]] = new ValueType[Option[T]](){
    override def convert(name: String, params: Map[String, String]): Option[T] = 
      if(condition(params)) Some(valueType.convert(name, params)) else None
      
    override def validate(name: String, params: Map[String, String]): Seq[(String, String)] = 
      if(condition(params)) valueType.validate(name, params) else Nil
  }
  
  /**
   * ValueType wrapper for the optional property which is available if checkbox is checked.
   */
  def optionalIfNotChecked[T](checkboxName: String, valueType: MappingValueType[T]): ValueType[Option[T]] = 
    optional({ params => boolean().convert(checkboxName, params) }, valueType)
  
  /**
   * ValueType wrapper for the optional property which is required if condition is true.
   */
  def optionalRequired[T](condition: (Map[String, String]) => Boolean, 
                          valueType: SingleValueType[T]): SingleValueType[Option[T]] = new SingleValueType[Option[T]](){
    def convert(value: String): Option[T] = 
      if(value == null || value.isEmpty) None else Some(valueType.convert(value))
      
    override def validate(name: String, value: String, params: Map[String, String]): Seq[(String, String)] =
      required.validate(name, value) match {
        case Some(error) if(condition(params)) => Seq(name -> error)
        case Some(error) => Nil
        case None => valueType.validate(name, value, params)
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
    
    def convert(value: String): T = valueType.convert(trim(value))
    
    override def validate(name: String, value: String, params: Map[String, String]): Seq[(String, String)] = 
      valueType.validate(name, trim(value), params)
    
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
    
    def convert(value: String): T = valueType.convert(value)
    
    override def validate(name: String, value: String, params: Map[String, String]): Seq[(String, String)] = 
      valueType.validate(label, value, params).map { case (label, message) => name -> message }
    
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constraints
  
  trait Constraint {
    
    def validate(name: String, value: String, params: Map[String, String]): Option[String] = validate(name, value)
    
    def validate(name: String, value: String): Option[String] = None
    
  }
  
  def required: Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] = {
      if(value == null || value.isEmpty) Some("%s is required.".format(name)) else None
    }
  }
  
  def required(message: String): Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] = 
      if(value == null || value.isEmpty) Some(message) else None
  }
  
  def maxlength(length: Int): Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] =
      if(value != null && value.length > length) Some("%s cannot be longer than %d characters.".format(name, length)) else None
  }
  
  def minlength(length: Int): Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] =
      if(value != null && value.length < length) Some("%s cannot be shorter than %d characters".format(name, length)) else None
  }
  
  def pattern(pattern: String, message: String = ""): Constraint = new Constraint {
    override def validate(name: String, value: String): Option[String] =
      if(value != null && !value.matches("^" + pattern + "$")){
        if(message.isEmpty) Some("%s must be '%s'.".format(name, pattern)) else Some(message)
      } else None
  }
  
}