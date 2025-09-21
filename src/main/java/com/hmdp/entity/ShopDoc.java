package com.hmdp.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;


@Data
@Document(indexName = "hmdp_shop")
public class ShopDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    private String address;

    @Field(type = FieldType.Keyword)
    private String area;

    @Field(type = FieldType.Long)
    private Long typeId;

    @Field(type = FieldType.Integer) // Keyword 表示不分词
    private Integer score;

    @Field(type = FieldType.Integer)
    private Long avgPrice;

    @Field(type = FieldType.Integer)
    private Integer comments;

}
