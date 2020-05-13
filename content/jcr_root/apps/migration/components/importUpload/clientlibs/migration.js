/*
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2015 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 */

(function(window, Granite, $, CUI) {
    "use strict";
    var initialConfig = null;
    var DEFAULT_MIGRATION_REPOSITORY_PATH = Granite.HTTP.getContextPath() + "/etc/migration/uploadFile/";
    var UPLOAD_AND_IMPORT_SERVLET = Granite.HTTP.getContextPath() + "/services/social/ugc/upload";

    $(function() {
        var select = new CUI.Select({ element: 'span.scf-js-file-selection-box' });
        $('section.wait-box').hide();
        //populate the file-selection drop-down list with all currently stored files
        $.get( UPLOAD_AND_IMPORT_SERVLET, null,
            function(dataRows) {
                if (dataRows.length > 0) {
                    $.each(dataRows, function() {
                        displayImportForm(this.files, this.folderName, this.filename, this.uploadDate, select);
                    });
                    $('form.scf-js-file-selection-form').show();
                }
            } ,
            "json" ).fail(function() {
                showAlert("error", "See server logs. Page could not be loaded");
            });

        // Hook the submit buttons
        // First button uploads a new zip archive containing migration data
        $("form.scf-js-file-upload-form button.coral-Button--primary").click(function(event) {
            event.preventDefault();
            var $target = $(event.currentTarget);
            var $form = $target.closest("form");
            var postData = new FormData();
            var input  = $('input', $form);
            var basePath = $('#basePath');
            if (input[0] && input[0].files && input[0].files[0]) {
                postData.append("file", input[0].files[0]);
                postData.append("basePath", basePath.val());
                $.ajax({
                    url: UPLOAD_AND_IMPORT_SERVLET,
                    type: 'POST',
                    data:  postData,
                    mimeType:"multipart/form-data",
                    contentType: false,
                    cache: false,
                    processData:false,
                    beforeSend:function() {
                        $('section.wait-box').show().fadeTo(500,0.5).click(function(event) {
                            event.preventDefault();
                            event.stopPropagation();
                        });
                    },
                    complete:function() {
                        $('section.wait-box').hide().unbind('click');
                    },
                    success:function(data) {
                        showAlert("success", "Your migration data has been saved.", "Success");
                        // display import view
                        data = JSON.parse(data);
                        if (data['files'].length > 0) {
                            displayImportForm(data['files'], data['folderName'], data['filename'], data['uploadDate'], select);
                            $('form.scf-js-file-selection-form').show();
                        }
                    },
                    error:function(jqXHR) {
                        showAlert("error", "Your migration data has not saved: " + jqXHR.statusCode() + " : " +
                            jqXHR.statusText, "Error");
                    }
                });
            } else {
                showAlert("error", "No file was selected for upload", "Error");
            }
            event.stopPropagation();
            return false;
        });

        //when file will be selected and it is different from the previously selected.
         $("form.scf-js-file-upload-form input.coral-FileUpload-input").change(function(event) {
               console.log(event.target.value);
               var files = event.target.files;
               if(files && files.length > 0){
                $("form.scf-js-file-upload-form input.coral-Form-field").val(event.target.files[0].name);
                //$("form.scf-js-file-upload-form input.coral-Form-field").prop('disabled', true);
                }
        })

        // second button imports ugc extracted from of the files extracted from a zip archive and stores it in a
        // specified ugc node
        $("form.scf-js-file-selection-form button.coral-Button--primary").click(function(event) {
            event.preventDefault();
            var $target = $(event.currentTarget);
            var $form = $target.closest("form");
            var input  = $('input', $form);
            var path = input.val();
            var filePath = select.getValue();
            if (path && filePath) {
                $.ajax({
                  url: UPLOAD_AND_IMPORT_SERVLET + "?filePath=" +
                    encodeURIComponent(DEFAULT_MIGRATION_REPOSITORY_PATH + filePath)
                    + "&path=" + encodeURIComponent(path),
                  type: 'PUT',
                  data: filePath,
                  beforeSend:function() {
                    $('section.wait-box').show().fadeTo(500,0.5).click(function(event) {
                        event.preventDefault();
                        event.stopPropagation();
                    });
                  },
                  complete:function() {
                    $('section.wait-box').hide().unbind('click');
                  },
                  success: function() {
                    var element = $('span.scf-js-file-selection-box');
                    $('span.coral-Select-button-text', element).empty().html('');
                    $('option[value="'+this.data+'"]', element).remove();
                    $('li[data-value="'+this.data+'"]', element).remove();
                    showAlert("success", "File imported.", "Success");
                  },
                  error: function() {
                    showAlert("error", "The file was not imported.", "Error");
                  }
                });
            } else if (!filePath) {
                showAlert("error", "First select a file to import", "Error");
            } else {
                showAlert("error", "Please enter the path to a node resource for import", "Error");
            }
            event.stopPropagation();
            return false;
        });

        // third button will delete a specified file from the store of files held in the default migration repository
        $("form.scf-js-file-selection-form button.delete-button").click(function(event) {
            event.preventDefault();
            var filePath = select.getValue();
            if (filePath) {
                var modal = new CUI.Modal({ element:'#myModal', visible: false });
                var yesButton = $('#myModal').find(".coral-Button--primary");
                yesButton.unbind("click");
                yesButton.bind("click", {filePath:filePath}, function(event) {
                    $.ajax({
                      url:  UPLOAD_AND_IMPORT_SERVLET + "?filePath=" +
                            encodeURIComponent(DEFAULT_MIGRATION_REPOSITORY_PATH + event.data.filePath),
                      type: 'DELETE',
                      data: event.data.filePath,
                      beforeSend:function() {
                        $('section.wait-box').show().fadeTo(500,0.5).click(function(event) {
                            event.preventDefault();
                            event.stopPropagation();
                        });
                      },
                      complete:function() {
                        $('section.wait-box').hide().unbind('click');
                      },
                      success: function() {
                        var element = $('span.scf-js-file-selection-box');
                        $('span.coral-Select-button-text', element).empty().html('');
                        $('option[value="'+this.data+'"]', element).remove();
                        $('li[data-value="'+this.data+'"]', element).remove();
                        showAlert("success", "File deleted.", "Success");
                      },
                      error: function() {
                        showAlert("error", "The file was not deleted.", "Error");
                      }
                    });
                });
                modal.show();
            } else {
                showAlert("error", "First select a file to delete", "Error");
            }
        });
    });

    var alert = null;
    var timer;

    function showAlert(type, content, heading) {
        if (alert !== null) {
            alert.set("type", type);
            alert.set("content", content);
            alert.set("heading", heading);
        } else {
            alert = new CUI.Alert({
                element: "#defaultAlert",
                heading: heading,
                content: content,
                closeable: true,
                type: type
            });
        }
        if (type === "success") {
            timer = window.setTimeout(function() {
                if (alert !== null) {
                    alert.hide();
                    $("#defaultAlert").hide();
                }
            }, 5000);
        }
        $("#defaultAlert").show();
    }

    function displayImportForm(files, folderName, filename, timestamp, select) {
        var date = new Date(timestamp);

        select.addGroup(date.toLocaleDateString() + ' ' + date.toLocaleTimeString() + ' - ' + filename, select.getItems().length);
        $.each(files, function () {
            var option = {};
            option.display = this;
            option.value = folderName + this;
            select.addOption(option);
        });
    }

})(window, Granite, Granite.$, CUI, Handlebars);