function bindDropArea(element, fileInput, dropCallback, enterCallback) {
    element.addEventListener("dragenter", function(e) {
        if (enterCallback)
            enterCallback();
    });
    element.addEventListener("dragover", function(e) {
        e.preventDefault();
    });
    element.addEventListener("drop", function(e) {
        e.preventDefault();
        e.stopPropagation();
        fileInput.files = e.dataTransfer.files;
        if (dropCallback) {
            dropCallback(e.dataTransfer.files);
        }
    });
}