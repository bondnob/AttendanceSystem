package com.attendance.auth.mapper;

import com.attendance.auth.model.UserMessage;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMessageMapper {

    @Insert("""
            INSERT INTO user_message
            (sender_user_id, target_user_id, title, content)
            VALUES
            (#{senderUserId}, #{targetUserId}, #{title}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserMessage message);

    @Select("""
            SELECT id, sender_user_id, target_user_id, title, content, created_at
            FROM user_message
            WHERE target_user_id = #{targetUserId}
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<UserMessage> findRecentByTargetUserId(@Param("targetUserId") Long targetUserId, @Param("limit") Integer limit);
}
