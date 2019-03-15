# Corda - Supply Chain demo CorDapp

This is a demo CorDapp, demonstrating a simplified supply chain workflow. 

The main parties of the workflow are the various **distributors** of a **cargo** item. 
Every item is can be scheduled to be transported from a distributor A to distributor Z via a pre-arranged list of co-operating distributors.
The main *flows* are the following:
* `EnterFlow`: supposed to be triggered by the distributor that will be the first in the chain, notifying all the involved distributors about the expected trip schedule of the item.
* `ArrivalFlow`: triggered when the cargo arrives at a distributor, notifying all the involved distributors that the cargo has arrived at the next station.
* `ExitFlow`: supposed to be triggered by the distributor that is the last in the chain, essentially notifying the other distributors that the cargo has been delivered to its final destination.

The cargo items are represented via a `LinearState` in the ledger.

## How to run the demo from terminal

* Deploy nodes
```
./gradlew deployNodes
```
* Start the nodes
```
./build/nodes/runnodes
```
* Create a trip for a cargo item, triggering an `EnterFlow` from distributor A.
```
flow start EnterCargoFlow tripDistributors: ["O=Distributor-A,L=London,C=GB", "O=Distributor-B,L=Zurich,C=CH", "O=Distributor-C,L=New York,C=US"], notary: "O=Notary,L=London,C=GB"
```
* Keep track of the ID returned from the previous command, which represents the cargo ID (say `1adcea7d-a81a-405e-80ac-ad08f860a75a`)
* Simulate arrival of the cargo in the next 2 stations (distributors B, C) by executing the following in their terminals:
```
flow start CargoArrivalReceiverFlow cargoID: 1adcea7d-a81a-405e-80ac-ad08f860a75a
```
* Simulate delivery of the cargo, by executing the following in the final distributor (C):
```
flow start ExitCargoFlow cargoID: 1adcea7d-a81a-405e-80ac-ad08f860a75a
```

## How to run the demo from the web interface

[TODO]