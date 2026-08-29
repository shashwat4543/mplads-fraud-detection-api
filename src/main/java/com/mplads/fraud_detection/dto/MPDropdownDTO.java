package com.mplads.fraud_detection.dto;

public class MPDropdownDTO {
    private Long id;
    private String name;
    private String constituency;
    private String state;
    private String party;

    public MPDropdownDTO(Long id, String name, String constituency, String state, String party) {
        this.id = id;
        this.name = name;
        this.constituency = constituency;
        this.state = state;
        this.party = party;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getConstituency() { return constituency; }
    public String getState() { return state; }
    public String getParty() { return party; }
}