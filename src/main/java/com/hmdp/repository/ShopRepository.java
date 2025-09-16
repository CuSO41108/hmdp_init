package com.hmdp.repository;

import com.hmdp.entity.ShopDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ShopRepository extends ElasticsearchRepository<ShopDoc, Long> {

    List<ShopDoc> findByNameOrAddress(String name, String address);
}
