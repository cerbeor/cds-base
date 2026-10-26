package gov.nist.healthcare.cds.domain.xml.beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PatientConditionType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="PatientConditionType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Condition" type="{https://fits.nist.gov/xml/}ConditionType" minOccurs="0"/>
 *         &lt;element name="DateValue" type="{https://fits.nist.gov/xml/}DateType" minOccurs="0"/>
 *         &lt;element name="NumericValue" type="{http://www.w3.org/2001/XMLSchema}float" minOccurs="0"/>
 *         &lt;element name="YesNoValue" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="AdministrativeGuidance" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="ID" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="type" use="required" type="{https://fits.nist.gov/xml/}ConditionValueTypeType" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PatientConditionType", propOrder = {
    "condition",
    "dateValue",
    "numericValue",
    "yesNoValue",
    "administrativeGuidance"
})
public class PatientConditionType {

    @XmlElement(name = "Condition")
    protected ConditionType condition;
    @XmlElement(name = "DateValue")
    protected DateType dateValue;
    @XmlElement(name = "NumericValue")
    protected Float numericValue;
    @XmlElement(name = "YesNoValue")
    protected Boolean yesNoValue;
    @XmlElement(name = "AdministrativeGuidance")
    protected String administrativeGuidance;
    @XmlAttribute(name = "ID", required = true)
    protected String id;
    @XmlAttribute(name = "type", required = true)
    protected ConditionValueTypeType type;

    public ConditionType getCondition() {
        return condition;
    }

    public void setCondition(ConditionType value) {
        this.condition = value;
    }

    public DateType getDateValue() {
        return dateValue;
    }

    public void setDateValue(DateType value) {
        this.dateValue = value;
    }

    public Float getNumericValue() {
        return numericValue;
    }

    public void setNumericValue(Float value) {
        this.numericValue = value;
    }

    public Boolean getYesNoValue() {
        return yesNoValue;
    }

    public void setYesNoValue(Boolean value) {
        this.yesNoValue = value;
    }

    public String getAdministrativeGuidance() {
        return administrativeGuidance;
    }

    public void setAdministrativeGuidance(String value) {
        this.administrativeGuidance = value;
    }

    public String getID() {
        return id;
    }

    public void setID(String value) {
        this.id = value;
    }

    public ConditionValueTypeType getType() {
        return type;
    }

    public void setType(ConditionValueTypeType value) {
        this.type = value;
    }

}
