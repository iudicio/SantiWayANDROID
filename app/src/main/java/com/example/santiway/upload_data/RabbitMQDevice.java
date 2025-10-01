package com.example.santiway.upload_data;

public class RabbitMQDevice {
    private String device_id;
    private String type;
    private String name;
    private Integer signal_strength;
    private Integer frequency;
    private String capabilities;
    private String vendor;
    private Integer cell_id;
    private Integer lac;
    private Integer mcc;
    private Integer mnc;
    private Integer psc;
    private Integer pci;
    private Integer tac;
    private Integer earfcn;
    private Integer arfcn;
    private Integer signal_quality;
    private String network_type;
    private Boolean is_registered;
    private Boolean is_neighbor;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Float location_accuracy;
    private Long timestamp;
    private String status;

    // Конструкторы
    public RabbitMQDevice() {}

    // Геттеры и сеттеры (в snake_case для соответствия JSON)
    public String getDevice_id() { return device_id; }
    public void setDevice_id(String device_id) { this.device_id = device_id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSignal_strength() { return signal_strength; }
    public void setSignal_strength(Integer signal_strength) { this.signal_strength = signal_strength; }

    // ... остальные геттеры/сеттеры в snake_case
    public Integer getFrequency() { return frequency; }
    public void setFrequency(Integer frequency) { this.frequency = frequency; }

    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public Integer getCell_id() { return cell_id; }
    public void setCell_id(Integer cell_id) { this.cell_id = cell_id; }

    public Integer getLac() { return lac; }
    public void setLac(Integer lac) { this.lac = lac; }

    public Integer getMcc() { return mcc; }
    public void setMcc(Integer mcc) { this.mcc = mcc; }

    public Integer getMnc() { return mnc; }
    public void setMnc(Integer mnc) { this.mnc = mnc; }

    public Integer getPsc() { return psc; }
    public void setPsc(Integer psc) { this.psc = psc; }

    public Integer getPci() { return pci; }
    public void setPci(Integer pci) { this.pci = pci; }

    public Integer getTac() { return tac; }
    public void setTac(Integer tac) { this.tac = tac; }

    public Integer getEarfcn() { return earfcn; }
    public void setEarfcn(Integer earfcn) { this.earfcn = earfcn; }

    public Integer getArfcn() { return arfcn; }
    public void setArfcn(Integer arfcn) { this.arfcn = arfcn; }

    public Integer getSignal_quality() { return signal_quality; }
    public void setSignal_quality(Integer signal_quality) { this.signal_quality = signal_quality; }

    public String getNetwork_type() { return network_type; }
    public void setNetwork_type(String network_type) { this.network_type = network_type; }

    public Boolean getIs_registered() { return is_registered; }
    public void setIs_registered(Boolean is_registered) { this.is_registered = is_registered; }

    public Boolean getIs_neighbor() { return is_neighbor; }
    public void setIs_neighbor(Boolean is_neighbor) { this.is_neighbor = is_neighbor; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getAltitude() { return altitude; }
    public void setAltitude(Double altitude) { this.altitude = altitude; }

    public Float getLocation_accuracy() { return location_accuracy; }
    public void setLocation_accuracy(Float location_accuracy) { this.location_accuracy = location_accuracy; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}