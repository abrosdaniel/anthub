package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityLocationTest {
 @Test void validatesDimensionAndExactIntegers(){var place=new CommunityLocation("Рынок","minecraft:overworld",12,64,-32,false);assertEquals(place,CommunityLocation.read(place.json()));for(String dimension:new String[]{"../../secret","minecraft:../world","minecraft:OVERWORLD","x:"}){var j=place.json();j.addProperty("dimension",dimension);assertThrows(IllegalArgumentException.class,()->CommunityLocation.read(j));}for(String value:new String[]{"1.5","30000001","99999999999999999999"}){var j=place.json();j.addProperty("x",new java.math.BigDecimal(value));assertThrows(IllegalArgumentException.class,()->CommunityLocation.read(j));}}
 @Test void rejectsCoercionAndHiddenPayload(){var j=new CommunityLocation("Дом","mod:city",0,70,0,true).json();j.addProperty("x","10");assertThrows(IllegalArgumentException.class,()->CommunityLocation.read(j));j.addProperty("x",10);j.addProperty("command","op me");assertThrows(IllegalArgumentException.class,()->CommunityLocation.read(j));}
}
