package response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserBody {
    private int id;
    private String name;
    private String photo;
    private String email;
    private boolean moderation;
    private long moderationCount;
    private boolean settings;
}
