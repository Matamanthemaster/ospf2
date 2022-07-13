#ifndef OSPFCLIB3_LIBRARY_H
#define OSPFCLIB3_LIBRARY_H

#include <stdint-gcc.h>

int TestJNR(const char *p_output);
int SendPacket(char *p_intName, unsigned char *p_intHWAddr, char *p_intIPS, unsigned char *p_ospfBuffer, int ospfBufferSize);
int __CreateSocket(char *device);
unsigned short __CalcChecksum(uint16_t *ptr, int nbytes);

#endif //OSPFCLIB3_LIBRARY_H
