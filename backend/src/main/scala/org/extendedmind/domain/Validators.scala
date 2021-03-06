package org.extendedmind.domain

import java.text.ParseException

object Validators {
  
  val TITLE_MAX_LENGTH = 128
  val DESCRIPTION_MAX_LENGTH = 1024
  
  // Pattern from: http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
  // removed uppercase to make sure no duplicates exist
  val emailPattern = ("^[_a-z0-9-\\+]+(\\.[_a-z0-9-]+)*@"
                    + "[a-z0-9-]+(\\.[a-z0-9]+)*(\\.[a-z]{2,})$").r

  val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd")
  dateFormat.setLenient(false)

  val timeFormat = new java.text.SimpleDateFormat("hh:mm")
  timeFormat.setLenient(false)
  
  def validateEmailAddress(email: String): Boolean = {
    if (email.length() > 100) false
    else emailPattern.pattern.matcher(email).matches()
  }
  
  def validatePassword(password: String): Boolean = {
    if (password.length() < 7 || password.length() > 100) false
    else true
  }
  
  def validateTitle(value: String): Boolean = {
    validateLength(value, TITLE_MAX_LENGTH)
  }
  
  def validateDescription(value: String): Boolean = {
    validateLength(value, DESCRIPTION_MAX_LENGTH)
  }
  
  def validateLength(value: String, maxLength: Int): Boolean = {
    if (value.length() > maxLength) false
    else true
  }
  
  def validateDateString(dateString: String): Boolean = {
    try {
      dateFormat.parse(dateString);
    } catch {
      case e: ParseException => return false
    }
    return true
  }
  
  def validateTimeString(timeString: String): Boolean = {
    try {
      timeFormat.parse(timeString);
    } catch {
      case e: ParseException => return false
    }
    return true
  }
}