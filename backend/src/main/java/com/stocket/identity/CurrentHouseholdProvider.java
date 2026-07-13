package com.stocket.identity;

@org.springframework.modulith.NamedInterface("api")
public interface CurrentHouseholdProvider {

    CurrentHousehold requireCurrent();
}
