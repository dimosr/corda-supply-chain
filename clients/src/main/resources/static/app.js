"use strict";

var nodeName, otherPartiesName;
var RELOAD_DELAY = 2500;

function showLoader() {
    $("#loader").show()
}

function hideLoader() {
    $("#loader").hide()
}

function populateNodeName(nodeContainerId, scheduleDistributorsListId) {
    $.ajax({
        url: "/node",
        beforeSend: function() {
            showLoader();
        },
        success: function( result ) {
            hideLoader();
            nodeName = result.legalIdentitiesAndCerts[0];
            $( "#" + nodeContainerId ).append( nodeName );
            var nodeItem = $("<li>", {
                text: nodeName,
                class: "list-group-item"
            });
            $( "#" + scheduleDistributorsListId ).html( nodeItem );
        }
    });
}

function populatePartiesName(selectorListId, formListId) {
    $.ajax({
        url: "/parties",
        beforeSend: function() {
            showLoader();
        },
        success: function( result ) {
            hideLoader();
            otherPartiesName = result;
            var listItems = otherPartiesName.map(name => $("<li>", {
                text: name,
                class: "list-group-item clickable-group-item"
            }))
            listItems.forEach(function(element) {
                element.click(function() {
                    $('#' + formListId).append(element.clone());
                    element.remove();
                });
                $('#' + selectorListId).append(element);
            });

        }
    });
}

function populateCargoItemsTable(cargoTablesId) {
    $.ajax({
            url: "/cargo",
            beforeSend: function() {
                showLoader();
            },
            success: function( result ) {
                hideLoader();
                var tableItems = result.map(cargoItem => {
                    var cargoIdColumn = $("<td>").text(cargoItem.cargoID.id);
                    var scheduleList = $("<ol>");
                    var scheduleDistributors = cargoItem.participatingDistributors.map(distributor => $("<li>").text(distributor));
                    scheduleList.append(scheduleDistributors);
                    var scheduleIdColumn = $("<td>").append(scheduleList);
                    var currentLocationColumn = $("<td>").text(cargoItem.currentDistributor);
                    var actionButtons = inferPossibleActionsForCargo(cargoItem.participatingDistributors, cargoItem.currentDistributor, cargoItem.cargoID.id);
                    var actionsColumn = $("<td>").append(actionButtons);

                    var cargoItemRow = $("<tr>");
                    cargoItemRow.append(cargoIdColumn);
                    cargoItemRow.append(scheduleIdColumn);
                    cargoItemRow.append(currentLocationColumn);
                    cargoItemRow.append(actionsColumn);

                    return cargoItemRow;
                });
                tableItems.forEach(function(element) {
                    $('#' + cargoTablesId + " tbody").append(element);
                });

            }
        });
}

function inferPossibleActionsForCargo(participatingDistributorsList, currentDistributor, cargoID) {
    var myIndex = participatingDistributorsList.indexOf(nodeName);
    var currentDistributorIndex = participatingDistributorsList.indexOf(currentDistributor);

    var actionButtons = [];

    if (currentDistributorIndex == myIndex - 1) {
        // we are the next destination
        var arrivedButton = $("<button>", {
            text: "Cargo arrived",
            class: "btn btn-primary",
            click: function() {
                var url = "/cargo/<cargoID>/arrived".replace("<cargoID>", cargoID);
                $.ajax({
                        url: url,
                        method: "POST",
                        contentType: "application/json",
                        beforeSend: function() {
                            showLoader();
                        },
                        success: function( result ) {
                            hideLoader();
                            alert("Cargo arrived with ID: " + cargoID);
                            setTimeout(location.reload(), RELOAD_DELAY);
                        }
                    });
            }
        });
        actionButtons.push(arrivedButton);
    }

    if (currentDistributorIndex == myIndex && myIndex == (participatingDistributorsList.length - 1)) {
        // we are the last destination and the cargo has already arrived
        var deliverButton = $("<button>", {
            text: "Deliver cargo",
            class: "btn btn-success",
            click: function() {
                var url = "/cargo/<cargoID>/deliver".replace("<cargoID>", cargoID);
                $.ajax({
                        url: url,
                        method: "POST",
                        contentType: "application/json",
                        beforeSend: function() {
                            showLoader();
                        },
                        success: function( result ) {
                            hideLoader();
                            alert("Cargo delivered with ID: " + cargoID);
                            setTimeout(location.reload(), RELOAD_DELAY);
                        }
                    });
            }
        });
        actionButtons.push(deliverButton);
    }

    return actionButtons;
}

function createSchedule(distributorsName) {
    if (distributorsName.length == 1) {
        alert("Shipment must contain more than 1 distributors.")
    } else {
        var data = {
                "distributorsName": distributorsName
            }
            $.ajax({
                url: "/schedule/create",
                method: "POST",
                contentType: "application/json",
                data: JSON.stringify(data),
                dataType: 'json',
                beforeSend: function() {
                    showLoader();
                },
                success: function( result ) {
                    hideLoader();
                    var cargoID = result.cargoID;
                    alert("Cargo scheduled with ID: " + cargoID);
                    setTimeout(location.replace("/index.html"), RELOAD_DELAY);
                }
            });
    }

}