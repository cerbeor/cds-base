package gov.nist.healthcare.cds.domain.xml.beans;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ConditionValueTypeType.
 *
 * <pre>
 * &lt;simpleType name="ConditionValueTypeType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="YesNo"/>
 *     &lt;enumeration value="Date"/>
 *     &lt;enumeration value="Numeric"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 *
 */
@XmlType(name = "ConditionValueTypeType")
@XmlEnum
public enum ConditionValueTypeType {

    @XmlEnumValue("YesNo")
    YES_NO("YesNo"),
    @XmlEnumValue("Date")
    DATE("Date"),
    @XmlEnumValue("Numeric")
    NUMERIC("Numeric");
    private final String value;

    ConditionValueTypeType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ConditionValueTypeType fromValue(String v) {
        for (ConditionValueTypeType c: ConditionValueTypeType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
