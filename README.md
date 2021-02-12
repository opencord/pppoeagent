PPPoEAgent : PPPoE Intermediate Agent application
=================================================

### What is PPPoEAgent?
As described in TR-101 Issue 2 - 3.9.2, the PPPoE Intermediate Agent supports the PPPoE access method and is a function placed on the Access Node in order to insert access loop identification.

### Description
The PPPoE Intermediate Agent intercepts all upstream PPPoE discovery stage packets, i.e. the PADI, PADR and upstream PADT packets, but does not modify the source or destination MAC address of these PPPoE discovery packets.
Upon receipt of a PADI or PADR packet sent by the PPPoE client, the Intermediate Agent adds a PPPoE TAG to the packet to be sent upstream. The TAG contains the identification of the access loop on which the PADI or PADR packet was received.
If a PADI or PADR packet exceeds the Ethernet MTU after adding the access loop identification TAG, the Intermediate Agent must drop the packet, and issue the corresponding PADO or PADS response with a Generic-Error TAG to the sender.