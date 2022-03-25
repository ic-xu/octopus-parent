package io.octopus.udp.sender;

/**
 *
 * DNS 最大是 512byte，我们这里最大 526byte ，也小于 576byte 或者 548byte
 *
 *send message
 * | messageSumSize| messageType |  sqment     |    messageId   |  messageBody|
 * |    31bit      |    1bit     |     16bit   |    64bit       |   33553920  |
 * |             4byte           |     2byte   |    8byte       |   512byte   |
 *
 *
 * ack :    全是 0b01111111
 * | messageType |  sqment     |    messageId   |
 * |     1byte    |  2byte     |    8byte   |
 *
 */
public class UDPProton {
}
