#include "library.h"

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netpacket/packet.h>
#include <errno.h>
#include <string.h>
#include <arpa/inet.h>
#include <net/ethernet.h>
#include <sys/ioctl.h>
#include <net/if.h>

int TestJNR(const char *p_output) {
    printf("JNR Works: ");

    int i;
    while (i != -1)
    {
        printf("%c", p_output[i]);

        i++;
        if (p_output[i] == 0x00)
            i = -1;
    }
    printf("\n");
    return 76;
}

int SendPacket(char *p_intName, unsigned char *p_intHWAddr, char *p_intIPS, unsigned char *p_ospfBuffer, int ospfBufferSize) {


    struct in_addr sourceIP;
    sourceIP.s_addr = inet_addr(p_intIPS);// CHANGE ME

    //Create a socket,
    int sock_fd = __CreateSocket(p_intName);
    if (!(sock_fd)) {
        perror("Socket file descriptor not found.");
        exit(EXIT_FAILURE);
    }





    //Ethernet Header
    unsigned char ethernetBuffer[14] = {0x01, 0x00, 0x5e, 0x00, 0x00, 0x05,//destination //multicast for OSPF
                                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//ipSource
                                        0x08, 0x00};//next header ip protocol
    int ethernetBufferSize = sizeof(ethernetBuffer);

    //substitute mac address into the ethernet buffer
    memcpy(ethernetBuffer + 6, p_intHWAddr, 6);





    //IP header
    int ipHeaderSize = 20;
    struct iphdr ipHeader;
    memset(&ipHeader, 0x0, ipHeaderSize);
    ipHeader.version = 4;
    ipHeader.ihl = ipHeaderSize >> 2;
    ipHeader.tos = 0b11000000;//1100 00 is CS6 -   00 is  ECN not ECT flag.
    ipHeader.tot_len = htons(ipHeaderSize + ospfBufferSize);
    ipHeader.id = htons(random() % 65535);
    //flags would go here
    ipHeader.frag_off = 0;
    ipHeader.ttl = 1;
    ipHeader.protocol = 0x59;
    //header checksum would go here
    ipHeader.saddr = sourceIP.s_addr;
    ipHeader.daddr = inet_addr("224.0.0.5");

    //substitute in checksum
    ipHeader.check = (unsigned short) __CalcChecksum((unsigned short *) &ipHeader, ipHeaderSize);





    //copy current buffers to output buffer.
    int outBufferSize = ethernetBufferSize + ipHeaderSize + ospfBufferSize;
    unsigned char outBuffer[300];
    memset(outBuffer, 0, 300);
    memcpy(outBuffer, ethernetBuffer, ethernetBufferSize);
    memcpy(outBuffer + ethernetBufferSize, &ipHeader, ipHeaderSize);
    memcpy(outBuffer + ethernetBufferSize + ipHeaderSize, p_ospfBuffer, ospfBufferSize);
    printf("buffer contents: ");
    for (int i = 0; i < outBufferSize; i++)
    {
        printf("%02x ", outBuffer[i]);
    }
    printf("\n\r");





    //Write output buffer to the socket file descriptor.
    int writeResult = send(sock_fd, (unsigned char *) outBuffer, outBufferSize, 0);
    printf("Length %i, should be %i", writeResult, outBufferSize);
    if (writeResult == -1) {
        perror("Write unsuccessful. Errno: " + errno);
        return 0;
    }

    return 1;
}

int __CreateSocket(char *device) {
    int sock_fd;
    struct ifreq ifr;
    struct sockaddr_ll sll;
    memset(&ifr, 0, sizeof(ifr));
    memset(&sll, 0, sizeof(sll));

    sock_fd = socket(PF_PACKET, SOCK_RAW, htons(ETH_P_ALL));

    if(sock_fd == 0) { printf("ERR: socket creation for device: %s\n", device); return 0; }

    strncpy(ifr.ifr_name, device, sizeof(ifr.ifr_name));
    if(ioctl(sock_fd, SIOCGIFINDEX, &ifr) == -1) {
        printf(" ERR: ioctl failed for device: %s\n", device);
        return 0;
    }

    sll.sll_family      = AF_PACKET;
    sll.sll_ifindex     = ifr.ifr_ifindex;
    sll.sll_protocol    = htons(89);
    if(bind(sock_fd, (struct sockaddr *) &sll, sizeof(sll)) == -1) { printf("ERR: bind failed for device: %s\n", device); return 0; }
    return sock_fd;
}

unsigned short __CalcChecksum(uint16_t *ptr, int nbytes) {
    register long sum=0;
    uint16_t oddbyte;
    register uint16_t answer;

    while(nbytes>1) { sum += *ptr++; nbytes -= 2; }
    if(nbytes == 1) { oddbyte = 0; *((unsigned char *) &oddbyte) = *(unsigned char *)ptr; sum += oddbyte; }
    sum  = (sum >> 16)+(sum & 0xffff); sum+=(sum >> 16); answer = ~sum;
    return(answer);
}
