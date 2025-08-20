package com.taffy.streamlink.models;

public class StreamLabsData {
    private int loyaltyPoints = 0;
    private long watchTimeMinutes = 0;
    private int bitsDonated = 0;
    private int subscriptionMonths = 0;

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int points) { this.loyaltyPoints = points; }
    public long getWatchTimeMinutes() { return watchTimeMinutes; }
    public void addWatchTime(long minutes) { this.watchTimeMinutes += minutes; }
    public int getBitsDonated() { return bitsDonated; }
    public void addBits(int bits) { this.bitsDonated += bits; }
    public int getSubscriptionMonths() { return subscriptionMonths; }
    public void addSubscriptionMonths(int months) { this.subscriptionMonths += months; }
}