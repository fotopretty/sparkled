const $ = require('jquery');

function animationPreview(animationService,
                          editorService,
                          stageRestService,
                          $sce,
                          _) {
    'ngInject';

    const activeLedClass = 'active-led';

    return {
        restrict: 'E',
        replace: true,
        templateUrl: 'editor/preview/animation-preview.directive.html',
        link: scope => {
            scope.editorService = editorService;
            stageRestService.getStage(1).then(stage => scope.stageSvg = $sce.trustAsHtml(stage.svg));

            scope.$watch(() => animationService.running, (isRunning, wasRunning) => {
                if (isRunning && !wasRunning) {
                    draw();
                }
            });

            function draw() {
                const requestId = requestAnimationFrame(draw);
                const frame = animationService.getFrame();
                if (!!frame) {
                    drawFrame(frame);
                } else {
                    $(`.${activeLedClass}`).css('fill', '').removeClass(activeLedClass);
                    cancelAnimationFrame(requestId);
                }
            }

            function drawFrame(frame) {
                _(frame).forOwn((value, key) => {
                    renderChannel(key, value);
                });
            }

            function renderChannel(key, leds) {
                const elements = $(`svg .${key} circle`);
                _(leds).forEach((led, index) => {
                    led = convertLed(led);
                    const $led = $(elements[index]);
                    const alpha = _.max([led.r, led.g, led.b]) / 255;
                    $led.css('fill', `rgba(${led.r},${led.g},${led.b},${alpha})`);

                    if (led.r + led.g + led.b > 0) {
                        $led.addClass(activeLedClass);
                    } else {
                        $led.removeClass(activeLedClass);
                    }
                });
            }

            // Convert from java's signed integer representation to unsigned.
            function convertLed(led) {
                return {
                    r: led.r & 0xFF,
                    g: led.g & 0xFF,
                    b: led.b & 0xFF
                }
            }
        }
    }
}

module.exports = {
    name: 'animationPreview',
    fn: animationPreview
};
