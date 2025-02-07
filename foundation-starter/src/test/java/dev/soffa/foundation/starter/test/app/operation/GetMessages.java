package dev.soffa.foundation.starter.test.app.operation;

import dev.soffa.foundation.context.Context;
import dev.soffa.foundation.core.Operation;
import dev.soffa.foundation.starter.test.app.model.Message;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GetMessages implements Operation<Void, List<Message>> {

    @Override
    public List<Message> handle(Void noarg, @NonNull Context ctx) {
        return new ArrayList<>();
    }

}
