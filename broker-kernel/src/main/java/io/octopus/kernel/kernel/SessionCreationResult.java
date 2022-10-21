package io.octopus.kernel.kernel;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/5 08:40
 */

public record SessionCreationResult(DefaultSession session, CreationModeEnum mode, Boolean alreadyStored) {}
