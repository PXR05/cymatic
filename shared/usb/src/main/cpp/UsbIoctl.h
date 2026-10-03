#pragma once

#include <cerrno>
#include <sys/ioctl.h>

inline int retryUsbIoctl(int fd, unsigned long command, void *argument) {
    int result;
    do {
        result = ioctl(fd, command, argument);
    } while (result < 0 && errno == EINTR);
    return result;
}
