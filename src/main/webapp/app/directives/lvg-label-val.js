(function () {

	'use strict';

	var directives = angular.module('lavagna.directives');

	directives.directive('lvgLabelVal', function ($filter, Card, LabelCache) {

		var loadListValue = function (labelId, listValueId, scope) {
			LabelCache.findLabelListValue(labelId, listValueId).then(function (listValue) {
				scope.displayValue = listValue.value;
			});
		};

		return {
			restrict: 'EA',
			scope: {
				value: '='
			},
			template: '<span data-bindonce="type" data-bindonce="readOnly">'
				+ '<span data-bo-if="!readOnly && type === \'USER\'"><span data-lvg-user="displayValue"></span></span>'
				+ '<span data-bo-if="readOnly && type === \'USER\'"><span data-lvg-user-ro="displayValue"></span></span>'
				+ '<span data-bo-if="!readOnly && type === \'CARD\'"><span data-no-name data-lvg-card="displayValue"></span></span>'
				+ '<span data-bo-if="readOnly && type === \'CARD\'"><span data-no-name data-lvg-card-ro="displayValue"></span></span>'
				+ '<span data-bo-if="type != \'USER\' && type != \'CARD\'" data-bindonce="displayValue" data-bo-bind="displayValue"></span></span>',
			link: function ($scope, $element, $attrs) {

				if ($scope.value === undefined || $scope.value === null) {
					return;
				}
				
				$scope.readOnly = $attrs.readOnly != undefined;

				$scope.type = $scope.value.labelValueType || $scope.value.type || $scope.value.labelType;

				var type = $scope.type;
				var value = $scope.value.value || $scope.value;
				if (type === 'STRING') {
					$scope.displayValue = value.valueString;
				} else if (type === 'INT') {
					$scope.displayValue = value.valueInt;
				} else if (type === 'USER') {
					$scope.displayValue = value.valueUser;
				} else if (type === 'CARD') {
					$scope.displayValue = value.valueCard;
				} else if (type === 'LIST') {
					loadListValue($scope.value.labelId, value.valueList, $scope);
				} else if (type === 'TIMESTAMP') {
					$scope.displayValue = $filter('date')(value.valueTimestamp, 'dd.MM.yyyy');
				}
			}
		};
	});

})();