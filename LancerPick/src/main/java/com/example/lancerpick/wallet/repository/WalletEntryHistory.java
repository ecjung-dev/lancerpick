package com.example.lancerpick.wallet.repository;
import java.util.List;
public interface WalletEntryHistory {List<WalletEntryView> findHistory(long memberId,int pageSize,long pageOffset);}
